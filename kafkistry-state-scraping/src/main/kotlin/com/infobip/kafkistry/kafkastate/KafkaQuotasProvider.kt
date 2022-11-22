package com.infobip.kafkistry.kafkastate

import com.infobip.kafkistry.kafka.KafkaClientProvider
import com.infobip.kafkistry.kafkastate.config.PoolingProperties
import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.repository.KafkaClustersRepository
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import org.springframework.stereotype.Component

@Component
class KafkaQuotasProvider(
    clustersRepository: KafkaClustersRepository,
    clusterFilter: ClusterEnabledFilter,
    poolingProperties: PoolingProperties,
    promProperties: PrometheusMetricsProperties,
    issuesRegistry: BackgroundJobIssuesRegistry,
    private val clientProvider: KafkaClientProvider
) : AbstractKafkaStateProvider<ClusterQuotas>(
    clustersRepository, clusterFilter, poolingProperties, promProperties, issuesRegistry,
) {

    companion object {
        const val CLIENT_QUOTAS = "client_quotas"
    }

    override val stateTypeName = CLIENT_QUOTAS

    override fun fetchState(kafkaCluster: KafkaCluster): ClusterQuotas {
        val quotas = clientProvider.doWithClient(kafkaCluster) {
            it.listQuotas().get()
        }
        return ClusterQuotas(
            quotas = quotas.associate { it.entity to it.properties }
        )
    }

}