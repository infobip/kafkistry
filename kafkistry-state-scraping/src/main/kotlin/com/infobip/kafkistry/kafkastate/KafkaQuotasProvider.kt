package com.infobip.kafkistry.kafkastate

import com.infobip.kafkistry.kafka.KafkaClientProvider
import com.infobip.kafkistry.kafkastate.config.PoolingProperties
import com.infobip.kafkistry.kafkastate.coordination.StateDataPublisher
import com.infobip.kafkistry.kafkastate.coordination.StateScrapingCoordinator
import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.repository.KafkaClustersRepository
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import org.springframework.stereotype.Component

@Component
class KafkaQuotasProvider(
    clustersRepository: KafkaClustersRepository,
    clusterFilter: ClusterEnabledFilter,
    promProperties: PrometheusMetricsProperties,
    poolingProperties: PoolingProperties,
    scrapingCoordinator: StateScrapingCoordinator,
    issuesRegistry: BackgroundJobIssuesRegistry,
    stateDataPublisher: StateDataPublisher,
    private val clientProvider: KafkaClientProvider
) : AbstractKafkaStateProvider<ClusterQuotas>(
    clustersRepository, clusterFilter, promProperties, poolingProperties,
    scrapingCoordinator, issuesRegistry, stateDataPublisher,
) {

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