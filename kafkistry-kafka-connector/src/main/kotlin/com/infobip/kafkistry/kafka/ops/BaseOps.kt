package com.infobip.kafkistry.kafka.ops

import com.infobip.kafkistry.kafka.ConfigValue
import com.infobip.kafkistry.kafka.PartitionAssignments
import com.infobip.kafkistry.kafka.ReplicaAssignment
import com.infobip.kafkistry.kafka.Version
import com.infobip.kafkistry.service.KafkaClusterManagementException
import kafka.zk.AdminZkClient
import kafka.zk.KafkaZkClient
import org.apache.kafka.clients.admin.AbstractOptions
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.ConfigEntry
import org.apache.kafka.common.KafkaFuture
import org.apache.kafka.common.TopicPartitionInfo
import scala.Option
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

//support clusters 2.0.0 or greater

abstract class BaseOps(
    private val ctx: ClientCtx,
) {

    companion object {
        val VERSION_0 = Version.of("0")
        val VERSION_2_2 = Version.of("2.2")
        val VERSION_2_3 = Version.of("2.3")
        val VERSION_2_4 = Version.of("2.4")
        val VERSION_2_6 = Version.of("2.6")
    }

    data class ClientCtx(
        val adminClient: AdminClient,
        val readRequestTimeoutMs: Long,
        val writeRequestTimeoutMs: Long,
        val currentClusterVersionRef: AtomicReference<Version?>,
        val zkConnectionRef: AtomicReference<String?>,
        val zkClientLazy: Lazy<KafkaZkClient>,
    )

    protected val adminClient: AdminClient = ctx.adminClient
    protected val zkClient: KafkaZkClient by ctx.zkClientLazy
    protected val clusterVersion: Version get() = ctx.currentClusterVersionRef.get() ?: VERSION_0

    @Suppress("UNCHECKED_CAST")
    fun <T> Any.cast(): T = this as T

    fun <T : AbstractOptions<T>> T.withReadTimeout(): T = also {
        timeoutMs(ctx.readRequestTimeoutMs.toInt())
    }

    fun <T : AbstractOptions<T>> T.withWriteTimeout(): T = also {
        timeoutMs(ctx.writeRequestTimeoutMs.toInt())
    }

    fun readTimeoutDuration() = Duration.ofMillis(ctx.readRequestTimeoutMs)
    fun writeTimeoutDuration() = Duration.ofMillis(ctx.writeRequestTimeoutMs)

    fun <T> KafkaFuture<T>.asCompletableFuture(ofWhat: String): CompletableFuture<T> {
        return CompletableFuture<T>().also {
            whenComplete { result, ex ->
                when (ex) {
                    null -> it.complete(result)
                    else -> it.completeExceptionally(
                        KafkaClusterManagementException("Execution exception for: $ofWhat", ex)
                    )
                }
            }
        }
    }

    fun <T> runOperation(what: String, operation: () -> T): T {
        return try {
            operation()
        } catch (ex: Throwable) {
            throw KafkaClusterManagementException("Exception while performing: $what", ex)
        }
    }

    inline fun <E1 : Enum<E1>, reified E2 : Enum<E2>> E1.convert(): E2 {
        return java.lang.Enum.valueOf(E2::class.java, name)
    }

    fun ConfigEntry.toTopicConfigValue() = ConfigValue(
        value = value(),
        default = isDefault,
        readOnly = isReadOnly,
        sensitive = isSensitive,
        source = source()
    )

    fun TopicPartitionInfo.toPartitionAssignments(): PartitionAssignments {
        val leaderBrokerId = leader()?.id()
        val inSyncBrokerIds = isr().map { it.id() }.toSet()
        return PartitionAssignments(
            partition = partition(),
            replicasAssignments = replicas().mapIndexed { index, broker ->
                ReplicaAssignment(
                    brokerId = broker.id(),
                    leader = leaderBrokerId == broker.id(),
                    inSyncReplica = broker.id() in inSyncBrokerIds,
                    preferredLeader = index == 0,
                    rank = index
                )
            }
        )
    }

    fun newZKAdminClient() = AdminZkClient(zkClient, Option.empty())

}

