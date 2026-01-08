package com.infobip.kafkistry.kafkastate.coordination

import com.hazelcast.core.EntryEvent
import com.hazelcast.core.HazelcastInstance
import com.hazelcast.map.listener.EntryUpdatedListener
import com.infobip.kafkistry.hostname.HostnameResolver
import com.infobip.kafkistry.kafkastate.StateData
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import org.slf4j.LoggerFactory

/**
 * Publisher for sharing scraped state data across multiple Kafkistry instances.
 */
interface StateDataPublisher {

    /**
     * Publish scraped state data for other instances to consume.
     *
     * @param stateData The state data to publish
     */
    fun <V> publishStateData(stateData: StateData<V>)

    /**
     * Subscribe to state data updates from other instances.
     *
     * @param stateTypeName The type of state to subscribe to
     * @param listener Callback invoked when state data is received
     */
    fun <V> subscribeToStateUpdates(
        stateTypeName: String,
        listener: (StateData<V>) -> Unit,
    )

    /**
     * Read the latest available state data if cached (for late joiners).
     *
     * @param stateTypeName The type of state to read
     * @param clusterIdentifier The cluster identifier
     * @return The latest state data, or null if not available
     */
    fun <V> readLatestStateIfAvailable(
        stateTypeName: String,
        clusterIdentifier: KafkaClusterIdentifier,
    ): StateData<V>?

    /**
     * Publish that sampling has started for a cluster.
     */
    fun publishSamplingStarted(event: SamplingStartedEvent)

    /**
     * Publish a sampled Kafka record for other instances to consume.
     *
     * @param record The sampled record event
     */
    fun publishSampledRecord(record: SampledConsumerRecord)

    /**
     * Publish that sampling has completed (successfully or failed) for a cluster.
     */
    fun publishSamplingCompleted(event: SamplingCompletedEvent)

    /**
     * Subscribe to sampling lifecycle events from other instances.
     */
    fun subscribeToSamplingEvents(listener: SamplingEventListener)
}

/**
 * Listener for sampling lifecycle events.
 */
interface SamplingEventListener {
    fun onSamplingStarted(event: SamplingStartedEvent) = Unit
    fun onSampledRecord(event: SampledConsumerRecord) = Unit
    fun onSamplingCompleted(event: SamplingCompletedEvent) = Unit
}

/**
 * No-op publisher for local-only mode (when Hazelcast is not available).
 * Does nothing - each instance operates independently.
 */
class LocalStateDataPublisher : StateDataPublisher {

    override fun <V> publishStateData(stateData: StateData<V>) {
        // No-op: no sharing in local mode
    }

    override fun <V> subscribeToStateUpdates(stateTypeName: String, listener: (StateData<V>) -> Unit) {
        // No-op: no subscription in local mode
    }

    override fun <V> readLatestStateIfAvailable(
        stateTypeName: String,
        clusterIdentifier: KafkaClusterIdentifier,
    ): StateData<V>? {
        return null  // No shared cache in local mode
    }

    override fun publishSamplingStarted(event: SamplingStartedEvent) {
        // No-op: no sharing in local mode
    }

    override fun publishSampledRecord(record: SampledConsumerRecord) {
        // No-op: no sharing in local mode
    }

    override fun publishSamplingCompleted(event: SamplingCompletedEvent) {
        // No-op: no sharing in local mode
    }

    override fun subscribeToSamplingEvents(listener: SamplingEventListener) {
        // No-op: no subscription in local mode
    }
}

/**
 * Hazelcast-based publisher that shares state data via distributed IMap.
 *
 * Winners publish their scraped data to Hazelcast.
 * Losers subscribe to updates and receive the data.
 * Late joiners can read cached data on startup.
 */
class HazelcastStateDataPublisher(
    hazelcastInstance: HazelcastInstance,
    hostnameResolver: HostnameResolver,
) : StateDataPublisher {

    private val hostname = hostnameResolver.hostname
    private val hMember = hazelcastInstance.name

    private val log = LoggerFactory.getLogger(HazelcastStateDataPublisher::class.java)

    // Distributed cache for sharing scraped state data
    private val stateCache = hazelcastInstance.getMap<String, SerializedStateData>(
        "kafkistry-state-scraping-cache"
    )

    // Distributed topic for sharing sampling events (started, records, completed)
    private val samplingEventsTopic = hazelcastInstance.getTopic<Any>(
        "kafkistry-sampling-events"
    )

    override fun <V> publishStateData(stateData: StateData<V>) = with(stateData) {
        try {
            val key = cacheKey(stateTypeName, clusterIdentifier)
            val kafkistryInstance = "$hostname-$hMember"
            val serialized = SerializedStateData.from(kafkistryInstance,this)
            stateCache.put(key, serialized)
            log.debug("Published state data for {}/{} to Hazelcast as {} (age: {}ms)", stateTypeName, clusterIdentifier, kafkistryInstance, System.currentTimeMillis() - lastRefreshTime)
        } catch (ex: Exception) {
            log.error("Failed to publish state data for {}/{}", stateTypeName, clusterIdentifier, ex)
        }
    }

    override fun <V> subscribeToStateUpdates(
        stateTypeName: String,
        listener: (StateData<V>) -> Unit
    ) {
        try {
            // Subscribe to entry updates in the Hazelcast map
            stateCache.addEntryListener(object : EntryUpdatedListener<String, SerializedStateData> {
                override fun entryUpdated(event: EntryEvent<String, SerializedStateData>) {
                    if (event.member.localMember()) {
                        log.trace("Ignoring entry update from local member key={}, instance={}", event.key, event.value.kafkistryInstance)
                        return
                    }
                    // Filter by state type name
                    if (!event.key.startsWith("$stateTypeName:")) {
                        return
                    }
                    try {
                        val stateData = event.value.toStateData<V>()
                        val age = System.currentTimeMillis() - stateData.lastRefreshTime
                        log.debug("Received shared state for {}/{} from {} (age: {}ms)",
                            stateTypeName, stateData.clusterIdentifier, event.value.kafkistryInstance, age)
                        listener(stateData)
                    } catch (ex: Exception) {
                        log.error("Failed to deserialize state data from Hazelcast for key: {}", event.key, ex)
                    }
                }
            }, true)  // includeValue = true

            log.info("Subscribed to shared state updates for {}", stateTypeName)
        } catch (ex: Exception) {
            log.error("Failed to subscribe to state updates for {}", stateTypeName, ex)
        }
    }

    override fun <V> readLatestStateIfAvailable(
        stateTypeName: String,
        clusterIdentifier: KafkaClusterIdentifier
    ): StateData<V>? = try {
        val key = cacheKey(stateTypeName, clusterIdentifier)
        stateCache[key]?.toStateData()
    } catch (ex: Exception) {
        log.error("Failed to read cached state for {}/{}", stateTypeName, clusterIdentifier, ex)
        null
    }

    override fun publishSamplingStarted(event: SamplingStartedEvent) = with(event) {
        try {
            samplingEventsTopic.publish(this)
            log.trace("Published sampling started for {}/{} ({} topics)", clusterIdentifier, samplingPosition, topics.size)
        } catch (ex: Exception) {
            log.error("Failed to publish sampling started for {}/{}", clusterIdentifier, samplingPosition, ex)
        }
    }

    override fun publishSampledRecord(record: SampledConsumerRecord) = with(record) {
        try {
            samplingEventsTopic.publish(this)
            log.trace("Published sampled record for {}/{}/{} (offset: {})", clusterIdentifier, topic, partition, offset)
        } catch (ex: Exception) {
            log.error("Failed to publish sampled record for {}/{}/{}", clusterIdentifier, topic, partition, ex)
        }
    }

    override fun publishSamplingCompleted(event: SamplingCompletedEvent) = with(event) {
        try {
            samplingEventsTopic.publish(event)
            log.trace("Published sampling completed for {}/{} (success: {})", clusterIdentifier, samplingPosition, success)
        } catch (ex: Exception) {
            log.error("Failed to publish sampling completed for {}/{}", clusterIdentifier, samplingPosition, ex)
        }
    }

    override fun subscribeToSamplingEvents(listener: SamplingEventListener) {
        try {
            samplingEventsTopic.addMessageListener { message ->
                if (message.publishingMember.localMember()) {
                    log.trace("Ignoring sampling event coming from itself")
                    return@addMessageListener
                }
                try {
                    with(message.messageObject) {
                        when (this) {
                            is SamplingStartedEvent -> {
                                log.debug("Received sampling started for {}/{} ({} topics)", clusterIdentifier, samplingPosition, topics.size)
                                listener.onSamplingStarted(this)
                            }

                            is SampledConsumerRecord -> {
                                log.trace("Received sampled record for {}/{}/{} (offset: {})", clusterIdentifier, topic, partition, offset)
                                listener.onSampledRecord(this)
                            }

                            is SamplingCompletedEvent -> {
                                log.debug("Received sampling completed for {}/{} (success: {})", clusterIdentifier, samplingPosition, success)
                                listener.onSamplingCompleted(this)
                            }
                        }
                    }
                } catch (ex: Exception) {
                    log.error("Failed to process sampling event", ex)
                }
            }
            log.info("Subscribed to sampling events from other instances")
        } catch (ex: Exception) {
            log.error("Failed to subscribe to sampling events", ex)
        }
    }

    private fun cacheKey(stateTypeName: String, clusterIdentifier: String): String {
        return "$stateTypeName:$clusterIdentifier"
    }
}
