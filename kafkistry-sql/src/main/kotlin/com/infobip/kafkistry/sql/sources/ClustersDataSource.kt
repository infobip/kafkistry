@file:Suppress("JpaDataSourceORMInspection")

package com.infobip.kafkistry.sql.sources

import com.infobip.kafkistry.kafka.BrokerRack
import com.infobip.kafkistry.kafka.NodeId
import com.infobip.kafkistry.kafka.QuorumReplicaState
import com.infobip.kafkistry.kafkastate.*
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.Tag
import com.infobip.kafkistry.service.RuleViolation
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.cluster.inspect.ClusterInspectIssue
import com.infobip.kafkistry.service.cluster.inspect.ClusterIssuesInspectorService
import com.infobip.kafkistry.service.renderMessage
import com.infobip.kafkistry.sql.*
import org.springframework.stereotype.Component
import jakarta.persistence.*
import java.time.Instant

@Component
class ClustersDataSource(
    private val clustersRegistry: ClustersRegistryService,
    private val kafkaStateProvider: KafkaClustersStateProvider,
    private val nodeDiskMetricsStateProvider: NodeDiskMetricsStateProvider,
    private val clusterIssuesInspectorService: ClusterIssuesInspectorService,
) : SqlDataSource<Cluster> {

    override fun modelAnnotatedClass(): Class<Cluster> = Cluster::class.java

    override fun supplyEntities(): List<Cluster> {
        val allClusters = clustersRegistry.listClusters()
        val allClusterStates = kafkaStateProvider.getAllLatestClusterStates()
        val brokersDiskMetrics = nodeDiskMetricsStateProvider.getAllLatestStates()
        return allClusters.map { cluster ->
            val clusterState = allClusterStates[cluster.identifier]
            val clusterDiskMetrics = brokersDiskMetrics[cluster.identifier]
            val clusterIssues = if (clusterState?.stateType == StateType.VISIBLE) {
                try {
                    clusterIssuesInspectorService.inspectClusterIssues(cluster.identifier)
                } catch (ex: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            mapCluster(cluster, clusterState, clusterDiskMetrics, clusterIssues)
        }
    }

    private fun mapCluster(
        kafkaCluster: KafkaCluster,
        clusterState: StateData<KafkaClusterState>?,
        diskMetricsState: StateData<ClusterNodeMetrics>?,
        clusterIssues: List<ClusterInspectIssue>,
    ): Cluster {
        return Cluster().apply {
            cluster = kafkaCluster.identifier
            state = clusterState?.stateType ?: StateType.UNKNOWN
            usingSsl = kafkaCluster.sslEnabled
            usingSasl = kafkaCluster.saslEnabled
            nodes = clusterState?.valueOrNull()?.clusterInfo?.nodes?.map {
                ClusterNode().apply {
                    nodeId = it.nodeId
                    host = it.host
                    port = it.port
                    roles = it.roles.joinToString(",")
                    rack = it.rack
                }
            }.orEmpty()
            tags = kafkaCluster.tags
            profiles = kafkaCluster.profiles.joinToString(",")
            metadata = clusterState?.valueOrNull()?.clusterInfo?.let {
                ClusterMetadata().apply {
                    clusterId = it.clusterId
                    connectionString = it.connectionString
                    controllerId = it.controllerId
                    zookeeperConnectionString = it.zookeeperConnectionString
                    clusterVersion = it.clusterVersion?.toString()
                    securityEnabled = it.securityEnabled
                    kraftEnabled = it.kraftEnabled
                    brokerConfigs = it.perBrokerConfig.flatMap { (broker, configs) ->
                        configs.map {
                            BrokerConfigEntry().apply {
                                brokerId = broker
                                existingEntry = it.toExistingKafkaConfigEntry()
                            }
                        }
                    }
                    nodeApiKeysZkMigration = it.apiKeys.map { (nodeId, apiKeys) ->
                        ClusterNodeApiZkMigration().apply {
                            this.nodeId = nodeId
                            zkMigrationEnabled = apiKeys.zkMigrationEnabled
                        }
                    }
                    nodeApiKeys = it.apiKeys.flatMap { (nodeId, apiKeys) ->
                        apiKeys.apiKeys.map { apiKey ->
                            ClusterNodeApiKeyInfo().apply {
                                this.nodeId = nodeId
                                apiKeyId = apiKey.id
                                apiKeyName = apiKey.metadata?.name
                                clusterAction = apiKey.metadata?.isClusterAction
                                forwardable = apiKey.metadata?.isForwardable
                                requiresDelayedAllocation = apiKey.metadata?.requiresDelayedAllocation
                                minRequiredInterBrokerMagic = apiKey.metadata?.minRequiredInterBrokerMagic
                                minVersion = apiKey.minVersion
                                maxVersion = apiKey.maxVersion
                                latestUsableVersion = apiKey.latestUsableVersion
                                unusableReason = apiKey.unusableReason
                            }
                        }
                    }
                }
            }
            nodeDiskMetrics = diskMetricsState?.valueOrNull()?.nodesMetrics.orEmpty().map { (node, diskMetrics) ->
                NodeDiskMetrics().apply {
                    nodeId = node
                    totalBytes = diskMetrics.total
                    freeBytes = diskMetrics.free
                }
            }
            issues = clusterIssues.map { issue ->
                ClusterIssue().apply {
                    name = issue.name
                    message = issue.violation.renderMessage()
                    severity = issue.violation.severity
                    checkerClassName = issue.violation.ruleClassName
                }
            }
            features = ClusterFeaturesMetadata().apply {
                clusterState?.valueOrNull()?.clusterInfo?.let { info ->
                    finalizedFeaturesEpoch = info.features.finalizedFeaturesEpoch
                    features = (info.features.finalizedFeatures.keys + info.features.supportedFeatures.keys).map {
                        ClusterFeature().apply {
                            name = it
                            finalizedMinVersion = info.features.finalizedFeatures[it]?.minVersion
                            finalizedMaxVersion = info.features.finalizedFeatures[it]?.maxVersion
                            supportedMinVersion = info.features.supportedFeatures[it]?.minVersion
                            supportedMaxVersion = info.features.supportedFeatures[it]?.maxVersion
                        }
                    }
                }
            }

            fun QuorumReplicaState.toDbReplicaState(): ClusterQuorumReplicaState {
                val it = this
                return ClusterQuorumReplicaState().apply {
                    replicaId = it.replicaId
                    logEndOffset = it.logEndOffset
                    lastFetchTimestamp = it.lastFetchTimestamp?.let { Instant.ofEpochMilli(it) }
                    lastCaughtUpTimestamp = it.lastFetchTimestamp?.let { Instant.ofEpochMilli(it) }
                }
            }
            quorumInfo = ClusterQuorumMetadata().apply {
                clusterState?.valueOrNull()?.clusterInfo?.let { info ->
                    leaderId = info.quorumInfo.leaderId
                    leaderEpoch = info.quorumInfo.leaderEpoch
                    highWatermark = info.quorumInfo.highWatermark
                    voters = info.quorumInfo.voters.map { it.toDbReplicaState() }
                    observers = info.quorumInfo.observers.map { it.toDbReplicaState() }
                }
            }
        }
    }


}

@Entity
@Table(name = "Clusters")
class Cluster {

    @Id
    lateinit var cluster: KafkaClusterIdentifier

    @Enumerated(EnumType.STRING)
    lateinit var state: StateType

    var usingSsl: Boolean? = null
    var usingSasl: Boolean? = null
    var profiles: String? = null

    @ElementCollection
    @JoinTable(name = "Clusters_Nodes")
    lateinit var nodes: List<ClusterNode>

    @ElementCollection
    @JoinTable(name = "Clusters_Tags")
    @Column(name = "tag")
    lateinit var tags: List<Tag>

    var metadata: ClusterMetadata? = null

    @ElementCollection
    @JoinTable(name = "Clusters_NodeDiskMetrics")
    lateinit var nodeDiskMetrics: List<NodeDiskMetrics>

    @ElementCollection
    @JoinTable(name = "Clusters_Issues")
    lateinit var issues: List<ClusterIssue>

    lateinit var features: ClusterFeaturesMetadata
    lateinit var quorumInfo: ClusterQuorumMetadata

}

@Embeddable
class ClusterNode {
    var nodeId: NodeId? = null
    lateinit var host: String
    var port: Int? = null
    lateinit var roles: String //CSV of List<ClusterNodeRole>
    var rack: BrokerRack? = null
}

@Embeddable
class ClusterMetadata {

    lateinit var clusterId: String
    var controllerId: Int? = null
    lateinit var connectionString: String
    lateinit var zookeeperConnectionString: String
    var clusterVersion: String? = null
    var securityEnabled: Boolean? = null
    var kraftEnabled: Boolean? = null

    @ElementCollection
    @JoinTable(name = "Clusters_BrokerConfigs")
    lateinit var brokerConfigs: List<BrokerConfigEntry>

    @ElementCollection
    @JoinTable(name = "Clusters_NodeApiKeys")
    lateinit var nodeApiKeys: List<ClusterNodeApiKeyInfo>

    @ElementCollection
    @JoinTable(name = "Clusters_NodeApiKeysZkMigration")
    lateinit var nodeApiKeysZkMigration: List<ClusterNodeApiZkMigration>

}

@Embeddable
class BrokerConfigEntry {

    @Column(nullable = false)
    var brokerId: NodeId? = null

    lateinit var existingEntry: ExistingConfigEntry
}

@Embeddable
class NodeDiskMetrics {

    @Column(nullable = false)
    var nodeId: NodeId? = null

    var totalBytes: Long? = null
    var freeBytes: Long? = null
}

@Embeddable
class ClusterIssue {

    lateinit var name: String
    lateinit var message: String

    var checkerClassName: String? = null
    @Enumerated(EnumType.STRING)
    var severity: RuleViolation.Severity? = null

}

@Embeddable
class ClusterFeaturesMetadata {

    var finalizedFeaturesEpoch: Long? = null
    @ElementCollection
    @JoinTable(name = "Clusters_Features")
    lateinit var features: List<ClusterFeature>
}


@Embeddable
class ClusterFeature {
    lateinit var name: String

    var finalizedMinVersion: String? = null
    var finalizedMaxVersion: String? = null

    var supportedMinVersion: String? = null
    var supportedMaxVersion: String? = null
}

@Embeddable
class ClusterQuorumMetadata {

    var leaderId: NodeId = 0
    var leaderEpoch: Long = 0
    var highWatermark: Long = 0

    @ElementCollection
    @JoinTable(name = "Clusters_Quorum_Voters")
    lateinit var voters: List<ClusterQuorumReplicaState>

    @ElementCollection
    @JoinTable(name = "Clusters_Quorum_Observers")
    lateinit var observers: List<ClusterQuorumReplicaState>
}

@Embeddable
class ClusterQuorumReplicaState {
    var replicaId: NodeId = 0
    var logEndOffset: Long = 0
    var lastFetchTimestamp: Instant? = null
    var lastCaughtUpTimestamp: Instant? = null
}

@Embeddable
class ClusterNodeApiZkMigration {

    @Column(nullable = false)
    var nodeId: NodeId? = null

    var zkMigrationEnabled: Boolean? = null
}

@Embeddable
class ClusterNodeApiKeyInfo {

    @Column(nullable = false)
    var nodeId: NodeId? = null

    @Column(nullable = false)
    var apiKeyId: Int? = null

    var apiKeyName: String? = null
    var clusterAction: Boolean? = null
    var forwardable: Boolean? = null
    var requiresDelayedAllocation: Boolean? = null
    var minRequiredInterBrokerMagic: Int? = null

    var minVersion: Int? = null
    var maxVersion: Int? = null
    var latestUsableVersion: Int? = null
    var unusableReason: String? = null

}



