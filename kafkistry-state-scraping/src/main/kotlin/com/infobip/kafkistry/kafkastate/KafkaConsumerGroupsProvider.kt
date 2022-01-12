package com.infobip.kafkistry.kafkastate

import com.infobip.kafkistry.kafka.KafkaClientProvider
import com.infobip.kafkistry.kafkastate.config.PoolingProperties
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.repository.KafkaClustersRepository
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.CompletionException

@Component
class KafkaConsumerGroupsProvider(
    clustersRepository: KafkaClustersRepository,
    clusterFilter: ClusterEnabledFilter,
    issuesRegistry: BackgroundJobIssuesRegistry,
    private val poolingProperties: PoolingProperties,
    private val clientProvider: KafkaClientProvider,
) : AbstractKafkaStateProvider<ClusterConsumerGroups>(clustersRepository, clusterFilter, poolingProperties, issuesRegistry) {

    override val stateTypeName = "consumer_groups"

    private val ignoreConsumerGroup = poolingProperties.ignoredConsumerPattern
        .takeIf { it.isNotEmpty() }
        ?.let { Regex(it) }

    override fun fetchState(kafkaCluster: KafkaCluster): ClusterConsumerGroups {
        val consumerGroupIds = clientProvider.doWithClient(kafkaCluster) { it.consumerGroups().get() }
        val consumers = consumerGroupIds
                .filterNot { ignoreConsumerGroup?.matches(it) ?: false }
                .chunked(clientProvider.perClusterConcurrency())
                .flatMap { consumersBatch ->
                    consumersBatch
                            .associateWith { groupId -> clientProvider.doWithClient(kafkaCluster) { it.consumerGroup(groupId) } }
                            .map { (groupId, valueFuture) ->
                                groupId to try {
                                    Maybe.Result(valueFuture.get())
                                } catch (ex: Throwable) {
                                    val exception = (ex as? CompletionException)?.cause ?: ex
                                    log.warn("Exception on fetching consumer '{}' on cluster '{}'",
                                            groupId, kafkaCluster.identifier, exception
                                    )
                                    Maybe.Absent(ex)
                                }
                            }
                }
                .toMap()
        return ClusterConsumerGroups(
                consumerGroups = consumers,
        )
    }

}
