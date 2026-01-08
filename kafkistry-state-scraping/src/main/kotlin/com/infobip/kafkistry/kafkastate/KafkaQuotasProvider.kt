package com.infobip.kafkistry.kafkastate

import com.infobip.kafkistry.kafka.KafkaClientProvider
import com.infobip.kafkistry.model.KafkaCluster
import org.springframework.stereotype.Component

@Component
class KafkaQuotasProvider(
    components: StateProviderComponents,
    private val clientProvider: KafkaClientProvider
) : AbstractKafkaStateProvider<ClusterQuotas>(components) {

    companion object {
        const val CLIENT_QUOTAS = "client_quotas"
    }

    override fun stateTypeName() = CLIENT_QUOTAS

    override fun fetchState(kafkaCluster: KafkaCluster): ClusterQuotas {
        val quotas = clientProvider.doWithClient(kafkaCluster) {
            it.listQuotas().get()
        }
        return ClusterQuotas(
            quotas = quotas.associate { it.entity.asID() to it.properties }
        )
    }

}