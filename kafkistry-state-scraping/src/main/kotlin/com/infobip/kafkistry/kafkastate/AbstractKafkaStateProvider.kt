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
    promProperties: PrometheusMetricsProperties,
    poolingProperties: PoolingProperties,
    scrapingCoordinator: com.infobip.kafkistry.kafkastate.coordination.StateScrapingCoordinator,
    private val issuesRegistry: BackgroundJobIssuesRegistry,
    private val stateDataPublisher: com.infobip.kafkistry.kafkastate.coordination.StateDataPublisher,
) : BaseKafkaStateProvider(clustersRepository, clusterFilter, promProperties, poolingProperties, scrapingCoordinator) {

    private val clusterStates: MutableMap<KafkaClusterIdentifier, StateData<V>> = ConcurrentHashMap()

    init {
        // Subscribe to shared state updates from other instances
        stateDataPublisher.subscribeToStateUpdates(stateTypeName()) { receivedState ->
            log.info("Received shared state for {}/{} from another instance (age: {}ms)",
                stateTypeName(), receivedState.clusterIdentifier,
                System.currentTimeMillis() - receivedState.lastRefreshTime)
            clusterStates[receivedState.clusterIdentifier] = receivedState
            // Notify coordinator that state was received (for waiting threads)
            scrapingCoordinator.notifyStateReceived(stateTypeName(), receivedState.clusterIdentifier)
        }

        log.info("Initialized state provider for {} with distributed coordination", stateTypeName())
    }

    fun getLatestState(kafkaClusterIdentifier: KafkaClusterIdentifier): StateData<V> {
        return clusterStates[kafkaClusterIdentifier]
            ?: StateData(StateType.UNKNOWN, kafkaClusterIdentifier, stateTypeName(), System.currentTimeMillis())
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
                issuesRegistry.clearJob(it.backgroundJob())
            }
            shouldRemove
        }
        disabledClusters.forEach { clusterIdentifier ->
            clusterStates.computeIfAbsent(clusterIdentifier) {
                StateData(StateType.DISABLED, it, stateTypeName(), System.currentTimeMillis())
            }
        }

        // Late joiner support: Try to load cached state for enabled clusters
        enabledClusters.forEach { clusterIdentifier ->
            if (!clusterStates.containsKey(clusterIdentifier)) {
                val cachedState = stateDataPublisher
                    .readLatestStateIfAvailable<V>(stateTypeName(), clusterIdentifier)
                    ?: return@forEach
                log.info("Late joiner: Loaded cached state for {}/{} (age: {}ms)",
                    stateTypeName(), clusterIdentifier, System.currentTimeMillis() - cachedState.lastRefreshTime)
                clusterStates[clusterIdentifier] = cachedState
            }
        }
    }

    /**
     * Allow implementations to know that cluster is removed from registry so that any kind of cleanup is easy to trigger
     */
    protected open fun clusterRemoved(clusterIdentifier: KafkaClusterIdentifier) = Unit

    override fun doRefreshCluster(kafkaCluster: KafkaCluster): RefreshStatus {
        log.debug("Refreshing {} for cluster '{}'", stateTypeName(), kafkaCluster.identifier)
        val jonExecution = issuesRegistry.newExecution(kafkaCluster.identifier.backgroundJob())
        val startTime = System.currentTimeMillis()
        fun durationSec() = (System.currentTimeMillis() - startTime) / 1000.0
        val clusterState = try {
            val stateValue = fetchState(kafkaCluster)
            log.debug("Refreshed {} of cluster '{}' in {} sec", stateTypeName(), kafkaCluster.identifier, durationSec())
            jonExecution.succeeded()
            StateData(StateType.VISIBLE, kafkaCluster.identifier, stateTypeName(), startTime, stateValue)
        } catch (ex: InvalidClusterIdException) {
            jonExecution.failed(ex.deepToString())
            StateData(StateType.INVALID_ID, kafkaCluster.identifier, stateTypeName(), startTime)
        } catch (ex: Throwable) {
            log.error("Exception while refreshing {} for cluster {}, setting it's state as 'unreachable' after {} sec",
                stateTypeName(), kafkaCluster.identifier, durationSec(), ex
            )
            jonExecution.failed(ex.deepToString())
            StateData(StateType.UNREACHABLE, kafkaCluster.identifier, stateTypeName(), startTime)
        }
        clusterStates[kafkaCluster.identifier] = clusterState
        return RefreshStatus(
                scrapeType = stateTypeName(),
                clusterState = clusterState.stateType,
                durationMs = System.currentTimeMillis() - startTime
        )
    }

    override fun publishScrapedState(clusterIdentifier: KafkaClusterIdentifier) {
        val stateData = clusterStates[clusterIdentifier]
        if (stateData != null) {
            stateDataPublisher.publishStateData(stateData)
            log.debug("Published scraped state for {}/{}", stateTypeName(), clusterIdentifier)
        } else {
            log.warn("Attempted to publish state for {}/{} but no state data found", stateTypeName(), clusterIdentifier)
        }
    }

    private fun KafkaClusterIdentifier.backgroundJob() = BackgroundJob.of(
        jobClass = this@AbstractKafkaStateProvider.javaClass.name,
        category = "kafka-scrape",
        phase = stateTypeName(),
        cluster = this,
        description = "Scraping of ${stateTypeName().replace("_", " ")} on cluster $this"
    )

    abstract fun fetchState(kafkaCluster: KafkaCluster): V

    protected class InvalidClusterIdException(message: String) : RuntimeException(message)

}