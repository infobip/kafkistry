package com.infobip.kafkistry.kafka.ops

import com.infobip.kafkistry.kafka.ClientQuota
import com.infobip.kafkistry.kafka.toJavaMap
import com.infobip.kafkistry.model.QuotaEntity
import com.infobip.kafkistry.model.QuotaProperties
import kafka.server.ConfigType
import kafka.zk.AdminZkClient
import org.apache.kafka.clients.admin.AlterClientQuotasOptions
import org.apache.kafka.clients.admin.DescribeClientQuotasOptions
import org.apache.kafka.common.config.internals.QuotaConfigs
import org.apache.kafka.common.config.internals.QuotaConfigs.*
import org.apache.kafka.common.quota.ClientQuotaAlteration
import org.apache.kafka.common.quota.ClientQuotaAlteration.Op
import org.apache.kafka.common.quota.ClientQuotaEntity
import org.apache.kafka.common.quota.ClientQuotaFilter
import org.apache.kafka.common.utils.Sanitizer
import java.util.*
import java.util.concurrent.CompletableFuture

class QuotasOps(
    clientCtx: ClientCtx,
) : BaseOps(clientCtx) {

    fun listQuotas(): CompletableFuture<List<ClientQuota>> {
        if (clusterVersion < VERSION_2_6) {
            return runOperation("list entity quotas") {
                val allEntityQuotas = fetchQuotasFromZk().map { (entity, quotas) ->
                    ClientQuota(entity, quotas.toQuotaProperties())
                }
                CompletableFuture.completedFuture(allEntityQuotas)
            }
        }
        return adminClient
            .describeClientQuotas(ClientQuotaFilter.all(), DescribeClientQuotasOptions().withReadTimeout())
            .entities()
            .asCompletableFuture("describe client quotas")
            .thenApply { entityQuotas ->
                entityQuotas.map { (entity, quotas) ->
                    ClientQuota(entity.toQuotaEntity(), quotas.toQuotaProperties())
                }
            }
    }

    private fun fetchQuotasFromZk(): Map<QuotaEntity, Map<String, Double>> {
        val adminZkClient = newZKAdminClient()
        fun Properties.toQuotaValues(): Map<String, Double> = this
            .mapKeys { it.key.toString() }
            .filterKeys { QuotaConfigs.isClientOrUserConfig(it) }
            .mapValues { it.value.toString().toDouble() }

        fun String.deSanitize(): String = when (this) {
            QuotaEntity.DEFAULT -> this
            else -> Sanitizer.desanitize(this)
        }

        val userConfig = adminZkClient.fetchAllEntityConfigs(ConfigType.User()).toJavaMap()
            .mapKeys { QuotaEntity(user = it.key.deSanitize()) }
        val clientConfig = adminZkClient.fetchAllEntityConfigs(ConfigType.Client()).toJavaMap()
            .mapKeys { QuotaEntity(clientId = it.key.deSanitize()) }
        val userClientConfig = adminZkClient
            .fetchAllChildEntityConfigs(ConfigType.User(), ConfigType.Client()).toJavaMap()
            .mapKeys {
                val (user, _, client) = it.key.split("/")
                QuotaEntity(user = user.deSanitize(), clientId = client.deSanitize())
            }
        return (userConfig + clientConfig + userClientConfig)
            .filterValues { it.isNotEmpty() }
            .mapValues { it.value.toQuotaValues() }
    }

    fun setClientQuotas(quotas: List<ClientQuota>): CompletableFuture<Unit> {
        val quotaAlterations = quotas.map { it.toQuotaAlteration() }
        return alterQuotas(quotaAlterations)
    }

    fun removeClientQuotas(quotaEntities: List<QuotaEntity>): CompletableFuture<Unit> {
        val quotaAlterations = quotaEntities.map {
            ClientQuotaAlteration(it.toClientQuotaEntity(), QuotaProperties.NONE.toQuotaAlterationOps())
        }
        return alterQuotas(quotaAlterations)
    }

    private fun alterQuotas(quotaAlterations: List<ClientQuotaAlteration>): CompletableFuture<Unit> {
        if (clusterVersion < VERSION_2_6) {
            val adminZkClient = newZKAdminClient()
            quotaAlterations.forEach {
                runOperation("alter entity quotas") {
                    alterQuotasOnZk(adminZkClient, it)
                }
            }
            return CompletableFuture.completedFuture(Unit)
        }
        return adminClient
            .alterClientQuotas(quotaAlterations, AlterClientQuotasOptions().withWriteTimeout())
            .all()
            .asCompletableFuture("alter client quotas")
            .thenApply { }
    }

    private fun alterQuotasOnZk(adminZkClient: AdminZkClient, alteration: ClientQuotaAlteration) {
        fun String?.sanitize() = when (this) {
            null -> QuotaEntity.DEFAULT
            else -> Sanitizer.sanitize(this)
        }

        val sanitizedEntityProps = alteration.entity().entries().mapValues { it.value.sanitize() }
        val user = sanitizedEntityProps[ClientQuotaEntity.USER]
        val clientId = sanitizedEntityProps[ClientQuotaEntity.CLIENT_ID]
        val (path, configType) = when {
            user != null && clientId != null -> "$user/clients/$clientId" to ConfigType.User()
            user != null && clientId == null -> user to ConfigType.User()
            user == null && clientId != null -> clientId to ConfigType.Client()
            else -> throw IllegalArgumentException("Both user and clientId are null")
        }
        val props = adminZkClient.fetchEntityConfig(configType, path)
        alteration.ops().forEach { op ->
            when (op.value()) {
                null -> props.remove(op.key())
                else -> {
                    val value = when (op.key()) {
                        PRODUCER_BYTE_RATE_OVERRIDE_CONFIG -> op.value().toLong().toString()
                        CONSUMER_BYTE_RATE_OVERRIDE_CONFIG -> op.value().toLong().toString()
                        REQUEST_PERCENTAGE_OVERRIDE_CONFIG -> op.value().toString()
                        else -> throw IllegalArgumentException("Unknown quota property key '${op.key()}'")
                    }
                    props[op.key()] = value
                }
            }
        }
        adminZkClient.changeConfigs(configType, path, props, false)
    }

    private fun ClientQuotaEntity.toQuotaEntity(): QuotaEntity {
        val entries = entries().mapValues { it.value.orDefault() }
        return QuotaEntity(
            user = entries[ClientQuotaEntity.USER],
            clientId = entries[ClientQuotaEntity.CLIENT_ID],
        )
    }

    private fun String?.orDefault() = this ?: QuotaEntity.DEFAULT
    private fun String.orNullIfDefault() = this.takeIf { it != QuotaEntity.DEFAULT }

    private fun QuotaEntity.toClientQuotaEntity() = ClientQuotaEntity(
        listOfNotNull(
            user?.let { ClientQuotaEntity.USER to it.orNullIfDefault() },
            clientId?.let { ClientQuotaEntity.CLIENT_ID to it.orNullIfDefault() },
        ).toMap()
    )

    private fun Map<String, Double>.toQuotaProperties(): QuotaProperties {
        return QuotaProperties(
            producerByteRate = this[PRODUCER_BYTE_RATE_OVERRIDE_CONFIG]?.toLong(),
            consumerByteRate = this[CONSUMER_BYTE_RATE_OVERRIDE_CONFIG]?.toLong(),
            requestPercentage = this[REQUEST_PERCENTAGE_OVERRIDE_CONFIG],
        )
    }

    private fun QuotaProperties.toQuotaAlterationOps(): List<Op> {
        return listOf(
            Op(PRODUCER_BYTE_RATE_OVERRIDE_CONFIG, producerByteRate?.toDouble()),
            Op(CONSUMER_BYTE_RATE_OVERRIDE_CONFIG, consumerByteRate?.toDouble()),
            Op(REQUEST_PERCENTAGE_OVERRIDE_CONFIG, requestPercentage),
        )
    }

    private fun ClientQuota.toQuotaAlteration() = ClientQuotaAlteration(
        entity.toClientQuotaEntity(), properties.toQuotaAlterationOps()
    )

}