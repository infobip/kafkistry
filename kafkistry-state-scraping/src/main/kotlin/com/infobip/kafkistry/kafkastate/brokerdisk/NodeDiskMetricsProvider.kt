package com.infobip.kafkistry.kafkastate.brokerdisk

import com.infobip.kafkistry.kafka.ClusterNode
import com.infobip.kafkistry.kafka.NodeId
import com.infobip.kafkistry.model.KafkaClusterIdentifier

interface NodeDiskMetricsProvider {

    /**
     * Fetch metrics for all nodes of particular [clusterIdentifier].
     * Implementations might not return map with metrics for all nodes (in case when no data is available),
     * Any exception might be thrown in case of any kind of network errors, or authorization exceptions occurs,
     * depending on what underlying implementation does.
     */
    fun nodesDisk(clusterIdentifier: KafkaClusterIdentifier, nodes: List<ClusterNode>): Map<NodeId, NodeDiskMetric>

    /**
     * @return `false` if this provider is not capable of collecting metrics for partcular [clusterIdentifier] so that
     *      other [BrokerDiskMetricsProvider] present in application might handle it
     */
    fun canHandle(clusterIdentifier: KafkaClusterIdentifier): Boolean = true

    companion object NoOp : NodeDiskMetricsProvider {
        override fun nodesDisk(clusterIdentifier: KafkaClusterIdentifier, nodes: List<ClusterNode>) = emptyMap<NodeId, NodeDiskMetric>()
    }
}

/**
 * snapshot of metrics from one broker, fields are `null` when absent
 */
data class NodeDiskMetric(
    val total: Long?,
    val free: Long?,
)