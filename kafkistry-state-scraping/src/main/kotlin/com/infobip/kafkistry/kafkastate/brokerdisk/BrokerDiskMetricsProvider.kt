package com.infobip.kafkistry.kafkastate.brokerdisk

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.ClusterBroker
import com.infobip.kafkistry.model.KafkaClusterIdentifier

interface BrokerDiskMetricsProvider {

    /**
     * Fetch metrics for all brokers of particular [clusterIdentifier].
     * Implementations might not return map with metrics for all brokers (in case when no data is available),
     * Any exception might be thrown in case of any kind of network errors, or authorization exceptions occurs,
     * depending on what underlying implementation does.
     */
    fun brokersDisk(clusterIdentifier: KafkaClusterIdentifier, brokers: List<ClusterBroker>): Map<BrokerId, BrokerDiskMetric>

    /**
     * @return `false` if this provider is not capable of collecting metrics for partcular [clusterIdentifier] so that
     *      other [BrokerDiskMetricsProvider] present in application might handle it
     */
    fun canHandle(clusterIdentifier: KafkaClusterIdentifier): Boolean = true

    companion object NoOp : BrokerDiskMetricsProvider {
        override fun brokersDisk(clusterIdentifier: KafkaClusterIdentifier, brokers: List<ClusterBroker>) = emptyMap<BrokerId, BrokerDiskMetric>()
    }
}

/**
 * snapshot of metrics from one broker, fields are `null` when absent
 */
data class BrokerDiskMetric(
    val total: Long?,
    val free: Long?,
)