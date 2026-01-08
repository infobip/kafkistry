package com.infobip.kafkistry.kafkastate.coordination

import com.hazelcast.core.HazelcastInstance
import com.infobip.kafkistry.hostname.HostnameResolver
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Coordinator for distributed state scraping across multiple Kafkistry instances.
 * Determines which instance should scrape each cluster in a given scraping round.
 */
interface StateScrapingCoordinator {

    /**
     * Attempt to acquire the right to scrape a specific cluster for this scraping round.
     *
     * @param stateTypeName The type of state being scraped (e.g., "cluster_state", "consumer_groups")
     * @param clusterIdentifier The Kafka cluster identifier
     * @param intervalMs The scraping interval in milliseconds
     * @return true if this instance won the race and should scrape, false if another instance won
     */
    fun tryAcquireScrapingLock(
        stateTypeName: String,
        clusterIdentifier: KafkaClusterIdentifier,
        intervalMs: Long
    ): Boolean

    /**
     * Wait for shared state to be published by another instance.
     * Called by instances that lost the scraping race.
     *
     * @param stateTypeName The type of state being scraped
     * @param clusterIdentifier The cluster to wait for
     * @param raceTimeMs Moment just before race was lost, accepting any event that arrives after it
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return true if state was received, false if timeout
     */
    fun waitForSharedState(
        stateTypeName: String,
        clusterIdentifier: KafkaClusterIdentifier,
        raceTimeMs: Long,
        timeoutMs: Long,
    ): Boolean

    /**
     * Notify that state has been received for a cluster.
     * Called by state subscribers when new state arrives.
     *
     * @param stateTypeName The type of state
     * @param clusterIdentifier The cluster identifier
     */
    fun notifyStateReceived(
        stateTypeName: String,
        clusterIdentifier: KafkaClusterIdentifier
    )
}

/**
 * Local-only coordinator that always grants scraping permission.
 * Used when Hazelcast is not available - each instance scrapes independently.
 */
class LocalStateScrapingCoordinator : StateScrapingCoordinator {

    override fun tryAcquireScrapingLock(
        stateTypeName: String,
        clusterIdentifier: KafkaClusterIdentifier,
        intervalMs: Long
    ): Boolean {
        return true  // Always scrape in local mode (no coordination)
    }

    override fun waitForSharedState(
        stateTypeName: String,
        clusterIdentifier: KafkaClusterIdentifier,
        raceTimeMs: Long,
        timeoutMs: Long
    ): Boolean {
        return true  // No waiting needed in local mode
    }

    override fun notifyStateReceived(
        stateTypeName: String,
        clusterIdentifier: KafkaClusterIdentifier
    ) {
        // No-op in local mode
    }
}

/**
 * Hazelcast-based coordinator that uses distributed locks for scraping coordination.
 * Only one instance across the cluster will win the race for each scraping round.
 *
 * Uses time-based scraping rounds to ensure fair distribution of work over time.
 * Lock TTL is 2x the interval to handle slow scrapes and crashed instances.
 */
class HazelcastStateScrapingCoordinator(
    hazelcastInstance: HazelcastInstance,
    hostnameResolver: HostnameResolver,
) : StateScrapingCoordinator {

    private val kafkistryInstance = hostnameResolver.hostname
    private val log = LoggerFactory.getLogger(HazelcastStateScrapingCoordinator::class.java)

    // Distributed lock cache for coordinating scraping races
    private val lockCache = hazelcastInstance.getMap<String, String>("state-scraping-locks")

    // Map of waiters for state updates: (stateTypeName:clusterIdentifier) -> list of latches
    private val stateUpdateWaiters = ConcurrentHashMap<String, MutableList<CountDownLatch>>()

    // Track recent notifications to handle race condition where notification happens before waiter registers
    // Key: stateTypeName:clusterIdentifier, Value: timestamp of notification
    private val recentNotifications = ConcurrentHashMap<String, Long>()

    override fun tryAcquireScrapingLock(
        stateTypeName: String,
        clusterIdentifier: KafkaClusterIdentifier,
        intervalMs: Long
    ): Boolean {
        // Calculate current scraping round based on time
        // This ensures idempotency - same round can't be won twice
        val scrapeRound = System.currentTimeMillis() / intervalMs
        val lockKey = "$stateTypeName:$clusterIdentifier:$scrapeRound"

        // Try to acquire lock with TTL = 2x interval
        // This handles slow scrapes and crashed instances
        val ttlMs = intervalMs * 2
        val oldHolder = lockCache.put(lockKey, kafkistryInstance, ttlMs, TimeUnit.MILLISECONDS)

        // Won the race if nobody held the lock before (oldHolder == null)
        val won = oldHolder == null

        if (won) {
            log.debug("Instance {} won scraping lock for {}/{} (round {})",
                kafkistryInstance, stateTypeName, clusterIdentifier, scrapeRound)
        } else {
            log.debug("Instance {} lost scraping race for {}/{} to {} (round {})",
                kafkistryInstance, stateTypeName, clusterIdentifier, oldHolder, scrapeRound)
        }

        return won
    }

    override fun waitForSharedState(
        stateTypeName: String,
        clusterIdentifier: KafkaClusterIdentifier,
        raceTimeMs: Long,
        timeoutMs: Long,
    ): Boolean {
        val startTime = System.currentTimeMillis()
        val key = waiterKey(stateTypeName, clusterIdentifier)

        // Check if notification already happened (race condition fix)
        // If state was published before we registered the waiter, we would miss the notification
        val notificationTime = recentNotifications[key]
        if (notificationTime != null && notificationTime > raceTimeMs) {
            val age = startTime - notificationTime
            log.debug("Found recent notification for {}/{} from {}ms ago, no need to wait", stateTypeName, clusterIdentifier, age)
            return true
        }

        // Create a latch for this waiter
        val latch = CountDownLatch(1)

        // Register the waiter
        stateUpdateWaiters.compute(key) { _, existing ->
            (existing ?: mutableListOf()).apply { add(latch) }
        }

        try {
            val received = latch.await(timeoutMs, TimeUnit.MILLISECONDS)

            if (received) {
                val duration = System.currentTimeMillis() - startTime
                log.debug("Received shared state for {}/{} after {}ms", stateTypeName, clusterIdentifier, duration)
                return true
            } else {
                log.warn("Timeout waiting for shared state for {}/{} after {}ms", stateTypeName, clusterIdentifier, timeoutMs)
                return false
            }
        } finally {
            // Clean up waiter if still present
            stateUpdateWaiters.computeIfPresent(key) { _, waiters ->
                waiters.remove(latch)
                if (waiters.isEmpty()) null else waiters
            }
        }
    }

    override fun notifyStateReceived(
        stateTypeName: String,
        clusterIdentifier: KafkaClusterIdentifier
    ) {
        val key = waiterKey(stateTypeName, clusterIdentifier)

        // Record notification timestamp to handle race condition
        // This allows waiters who register late to detect that state was already published
        recentNotifications[key] = System.currentTimeMillis()

        // Clean up old notification timestamps to prevent memory leak
        // Keep only notifications from the last 5 minutes
        val fiveMinutesAgo = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5)
        recentNotifications.entries.removeIf { it.value < fiveMinutesAgo }

        // Notify all waiters for this state
        stateUpdateWaiters.remove(key)?.forEach { latch ->
            try {
                latch.countDown()
            } catch (ex: Exception) {
                log.warn("Error notifying state update waiter for {}/{}", stateTypeName, clusterIdentifier, ex)
            }
        }
    }

    private fun waiterKey(stateTypeName: String, clusterIdentifier: String): String {
        return "$stateTypeName:$clusterIdentifier"
    }
}
