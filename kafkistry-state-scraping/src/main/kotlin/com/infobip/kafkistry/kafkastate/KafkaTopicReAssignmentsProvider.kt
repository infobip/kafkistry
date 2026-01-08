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
class KafkaTopicReAssignmentsProvider(
    clustersRepository: KafkaClustersRepository,
    clusterFilter: ClusterEnabledFilter,
    promProperties: PrometheusMetricsProperties,
    poolingProperties: PoolingProperties,
    scrapingCoordinator: StateScrapingCoordinator,
    issuesRegistry: BackgroundJobIssuesRegistry,
    stateDataPublisher: StateDataPublisher,
    private val clientProvider: KafkaClientProvider
) : AbstractKafkaStateProvider<TopicPartitionReAssignments>(
    clustersRepository, clusterFilter, promProperties, poolingProperties,
    scrapingCoordinator, issuesRegistry, stateDataPublisher,
) {

    companion object {
        const val TOPIC_RE_ASSIGNMENTS = "topic_re_assignments"
    }

    override fun stateTypeName() = TOPIC_RE_ASSIGNMENTS

    override fun fetchState(kafkaCluster: KafkaCluster): TopicPartitionReAssignments {
        val topicPartitionReAssignments = clientProvider.doWithClient(kafkaCluster) {
            it.listReAssignments().get()
        }
        return TopicPartitionReAssignments(topicPartitionReAssignments)
    }

}