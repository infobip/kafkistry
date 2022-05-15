package com.infobip.kafkistry.service.cluster

import com.infobip.kafkistry.kafkastate.KafkaClustersStateProvider
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import org.springframework.stereotype.Service

@Service
class ClusterStatusService(
    private val kafkaClustersRegistry: ClustersRegistryService,
    private val kafkaClustersStateProvider: KafkaClustersStateProvider,
) {

    fun clustersState(): List<ClusterStatus> {
        val clusters = kafkaClustersRegistry.listClusters()
        return clusters
            .map { clusterState(it) }
            .sortedBy { it.cluster.identifier }
            .sortedBy { if (it.clusterState == StateType.DISABLED) 1 else 0 }
    }

    fun clusterState(clusterIdentifier: KafkaClusterIdentifier): ClusterStatus {
        val cluster = kafkaClustersRegistry.getCluster(clusterIdentifier)
        return clusterState(cluster)
    }

    fun clusterState(cluster: KafkaCluster): ClusterStatus {
        val stateData = kafkaClustersStateProvider.getLatestClusterState(cluster.identifier)
        return ClusterStatus(
            lastRefreshTime = stateData.lastRefreshTime,
            cluster = cluster,
            clusterInfo = stateData.valueOrNull()?.clusterInfo,
            clusterState = stateData.stateType,
        )
    }
}