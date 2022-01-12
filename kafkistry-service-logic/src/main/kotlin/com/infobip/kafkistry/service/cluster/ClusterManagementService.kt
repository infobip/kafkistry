package com.infobip.kafkistry.service.cluster

import com.infobip.kafkistry.events.*
import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.service.KafkaClusterManagementException
import org.springframework.stereotype.Service

@Service
class ClusterManagementService(
    private val clientProvider: KafkaClientProvider,
    private val clustersRegistry: ClustersRegistryService,
    private val kafkaStateProvider: KafkaClustersStateProvider,
    private val eventPublisher: EventPublisher,
) {

    fun applyAllBrokersThrottle(clusterIdentifier: KafkaClusterIdentifier, throttleRate: ThrottleRate) {
        val kafkaCluster = clustersRegistry.getCluster(clusterIdentifier)
        val results = clientProvider.doWithClient(kafkaCluster) { client ->
            val brokerIds = client.clusterInfo(clusterIdentifier).get().nodeIds
            val futures = brokerIds.associateWith {
                client.updateThrottleRate(it, throttleRate)
            }
            futures.mapValues { runCatching { it.value.get() } }
        }
        if (results.values.any { it.isSuccess }) {
            kafkaStateProvider.refreshClusterState(clusterIdentifier)
            eventPublisher.publish(ClusterThrottleUpdateEvent(clusterIdentifier))
        }
        if (results.values.any { it.isFailure }) {
            val failedBrokers = results.entries.filter { it.value.isFailure }.map { it.key }
            val firstFailCause = results.values.mapNotNull { it.exceptionOrNull() }.first()
            throw KafkaClusterManagementException("Applying throttle $throttleRate failed for brokers: $failedBrokers", firstFailCause)
        }
    }

    fun applyBrokerThrottle(clusterIdentifier: KafkaClusterIdentifier, brokerId: BrokerId, throttleRate: ThrottleRate) {
        val kafkaCluster = clustersRegistry.getCluster(clusterIdentifier)
        clientProvider.doWithClient(kafkaCluster) {
            it.updateThrottleRate(brokerId, throttleRate).get()
        }
        kafkaStateProvider.refreshClusterState(clusterIdentifier)
        eventPublisher.publish(ClusterThrottleUpdateEvent(clusterIdentifier, brokerId))
    }

}

