package com.infobip.kafkistry.service.kafkastreams

import com.infobip.kafkistry.kafkastate.ClusterConsumerGroups
import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import com.infobip.kafkistry.kafkastate.KafkaConsumerGroupsProvider
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.KafkistryIllegalStateException
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class KStreamsAppsProvider(
    private val clustersStateProvider: KafkaClustersStateProvider,
    private val consumerGroupsProvider: KafkaConsumerGroupsProvider,
    private val kStreamsAppsDetector: KStreamsAppsDetector,
    private val issuesRegistry: BackgroundJobIssuesRegistry,
) {

    private val clusterKStreamApps = ConcurrentHashMap<KafkaClusterIdentifier, ClusterKStreamsContext>()

    fun clusterKStreamApps(
        clusterIdentifier: KafkaClusterIdentifier
    ): List<KafkaStreamsApp> = clusterKStreamApps[clusterIdentifier]?.apps.orEmpty()

    fun topicKStreamAppsInvolvement(
        clusterIdentifier: KafkaClusterIdentifier,
        topicName: TopicName,
    ): TopicKStreamsInvolvement = clusterKStreamApps[clusterIdentifier]
        ?.topicInvolvements
        ?.get(topicName)
        ?: TopicKStreamsInvolvement.NONE

    fun consumerGroupKStreamApp(
        clusterIdentifier: KafkaClusterIdentifier,
        consumerGroupId: ConsumerGroupId,
    ): KafkaStreamsApp? = clusterKStreamApps[clusterIdentifier]?.appIdApp?.get(consumerGroupId)

    fun kStreamApp(
        clusterIdentifier: KafkaClusterIdentifier,
        kStreamAppId: KStreamAppId,
    ): KafkaStreamsApp {
        val clusterAppsCtx = clusterKStreamApps[clusterIdentifier]
            ?: throw KafkistryIllegalStateException("No KStream apps info for cluster '$clusterIdentifier'")
        return clusterAppsCtx.appIdApp[kStreamAppId]
            ?: run {
                val msg = StringBuilder().append("Can't find KStream app '$kStreamAppId' @ '$clusterIdentifier'")
                if (clusterAppsCtx.groupsStateType != StateType.VISIBLE) {
                    msg.append("; consumer groups scraping state = ${clusterAppsCtx.groupsStateType}")
                }
                if (clusterAppsCtx.topicsStateType != StateType.VISIBLE) {
                    msg.append("; topics scraping state = ${clusterAppsCtx.topicsStateType}")
                }
                throw KafkistryIllegalStateException(msg.toString())
            }
    }

    fun allClustersKStreamApps(): Map<KafkaClusterIdentifier, List<KafkaStreamsApp>> {
        return clusterKStreamApps.mapValues { it.value.apps }
    }

    @Scheduled(
        fixedRateString = "#{poolingProperties.intervalMs()}",
        initialDelayString = "#{poolingProperties.intervalMs() / 2}",
    )
    fun refreshAll() = issuesRegistry.doCapturingException("kStreamApps", "KStream apps detection") {
        doRefresh()
    }

    private fun doRefresh() {
        val allClusterStates = clustersStateProvider.getAllLatestClusterStates()
        val allGroupsStates = consumerGroupsProvider.getAllLatestStates()
        val kStreamApps = (allGroupsStates.keys + allGroupsStates.keys)
            .associateWith { clusterIdentifier ->
                val topicsStateData = allClusterStates[clusterIdentifier]
                val groupsStateData = allGroupsStates[clusterIdentifier]
                val kStreamApps = kStreamsAppsDetector.findKStreamApps(
                    clusterIdentifier = clusterIdentifier,
                    clusterConsumerGroups = groupsStateData?.valueOrNull() ?: ClusterConsumerGroups(emptyMap()),
                    topics = topicsStateData?.valueOrNull()?.topics.orEmpty(),
                )
                indexApps(
                    groupsStateType = groupsStateData?.stateType ?: StateType.UNKNOWN,
                    topicsStateType = topicsStateData?.stateType ?: StateType.UNKNOWN,
                    kStreamApps = kStreamApps
                )
            }
        clusterKStreamApps.putAll(kStreamApps)
        clusterKStreamApps.keys.retainAll(kStreamApps.keys)
    }

    private fun indexApps(
        groupsStateType: StateType,
        topicsStateType: StateType,
        kStreamApps: List<KafkaStreamsApp>
    ): ClusterKStreamsContext {
        val topicAsInput = kStreamApps
            .flatMap { app -> app.inputTopics.map { it to app } }
            .groupBy({ it.first }, { it.second })
        val topicAsInternal = kStreamApps
            .flatMap { app -> app.kStreamInternalTopics.map { it to app } }
            .toMap()
        val topicInvolvements = (topicAsInput.keys + topicAsInternal.keys).associateWith { topic ->
            TopicKStreamsInvolvement(
                inputOf = topicAsInput[topic].orEmpty(),
                internalIn = topicAsInternal[topic],
            )
        }
        val appsMap = kStreamApps.associateBy { it.kafkaStreamAppId }
        return ClusterKStreamsContext(
            groupsStateType = groupsStateType,
            topicsStateType = topicsStateType,
            apps = kStreamApps,
            topicInvolvements = topicInvolvements,
            appIdApp = appsMap
        )
    }

    private data class ClusterKStreamsContext(
        val groupsStateType: StateType,
        val topicsStateType: StateType,
        val apps: List<KafkaStreamsApp>,
        val topicInvolvements: Map<TopicName, TopicKStreamsInvolvement>,
        val appIdApp: Map<KStreamAppId, KafkaStreamsApp>,
    )

}