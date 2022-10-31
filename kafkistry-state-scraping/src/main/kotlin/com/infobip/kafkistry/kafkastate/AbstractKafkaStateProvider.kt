package com.infobip.kafkistry.kafkastate

import com.infobip.kafkistry.utils.deepToString
import com.infobip.kafkistry.kafkastate.config.PoolingProperties
import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.repository.KafkaClustersRepository
import com.infobip.kafkistry.service.background.BackgroundJob
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import java.util.concurrent.ConcurrentHashMap

abstract class AbstractKafkaStateProvider<V>(
    clustersRepository: KafkaClustersRepository,
    clusterFilter: ClusterEnabledFilter,
    poolingProperties: PoolingProperties,
    promProperties: PrometheusMetricsProperties,
    private val issuesRegistry: BackgroundJobIssuesRegistry
) : BaseKafkaStateProvider(clustersRepository, clusterFilter, poolingProperties, promProperties) {

    abstract val stateTypeName: String

    private val clusterStates: MutableMap<KafkaClusterIdentifier, StateData<V>> = ConcurrentHashMap()

    fun getLatestState(kafkaClusterIdentifier: KafkaClusterIdentifier): StateData<V> {
        return clusterStates[kafkaClusterIdentifier]
            ?: StateData(StateType.UNKNOWN, kafkaClusterIdentifier, stateTypeName, System.currentTimeMillis())
    }

    fun getLatestStateValue(kafkaClusterIdentifier: KafkaClusterIdentifier): V {
        return getLatestState(kafkaClusterIdentifier).value()
    }

    fun getAllLatestStates(): Map<KafkaClusterIdentifier, StateData<V>> = clusterStates.toMap()

    fun listAllLatestStates(): List<StateData<V>> = clusterStates.values.toList()

    override fun setupCachedState(enabledClusters: List<KafkaClusterIdentifier>, disabledClusters: List<KafkaClusterIdentifier>) {
        clusterStates.keys.removeIf {
            val shouldRemove = it !in enabledClusters && it !in disabledClusters
            if (shouldRemove) {
                clusterRemoved(it)
                issuesRegistry.clearIssue(it.backgroundJob())
            }
            shouldRemove
        }
        disabledClusters.forEach { clusterIdentifier ->
            clusterStates.computeIfAbsent(clusterIdentifier) {
                StateData(StateType.DISABLED, it, stateTypeName, System.currentTimeMillis())
            }
        }
    }

    /**
     * Allow implementations to know that cluster is removed from registry so that any kind of cleanup is easy to trigger
     */
    protected open fun clusterRemoved(clusterIdentifier: KafkaClusterIdentifier) = Unit

    override fun doRefreshCluster(kafkaCluster: KafkaCluster): RefreshStatus {
        log.debug("Refreshing {} for cluster '{}'", stateTypeName, kafkaCluster.identifier)
        val backgroundJob = kafkaCluster.identifier.backgroundJob()
        val startTime = System.currentTimeMillis()
        fun durationSec() = (System.currentTimeMillis() - startTime) / 1000.0
        val clusterState = try {
            val stateValue = fetchState(kafkaCluster)
            log.debug("Refreshed {} of cluster '{}' in {} sec", stateTypeName, kafkaCluster.identifier, durationSec())
            issuesRegistry.clearIssue(backgroundJob)
            StateData(StateType.VISIBLE, kafkaCluster.identifier, stateTypeName, startTime, stateValue)
        } catch (ex: InvalidClusterIdException) {
            issuesRegistry.reportIssue(backgroundJob, ex.deepToString())
            StateData(StateType.INVALID_ID, kafkaCluster.identifier, stateTypeName, startTime)
        } catch (ex: Throwable) {
            log.error("Exception while refreshing {} for cluster {}, setting it's state as 'unreachable' after {} sec",
                    stateTypeName, kafkaCluster, durationSec(), ex
            )
            issuesRegistry.reportIssue(backgroundJob, ex.deepToString())
            StateData(StateType.UNREACHABLE, kafkaCluster.identifier, stateTypeName, startTime)
        }
        clusterStates[kafkaCluster.identifier] = clusterState
        return RefreshStatus(
                scrapeType = stateTypeName,
                clusterState = clusterState.stateType,
                durationMs = System.currentTimeMillis() - startTime
        )
    }

    private fun KafkaClusterIdentifier.backgroundJob() = BackgroundJob.of(
        category = "kafka-scrape",
        phase = stateTypeName,
        cluster = this,
        description = "Scraping of ${stateTypeName.replace("_", " ")} on cluster $this"
    )

    abstract fun fetchState(kafkaCluster: KafkaCluster): V

    protected class InvalidClusterIdException(message: String) : RuntimeException(message)

}