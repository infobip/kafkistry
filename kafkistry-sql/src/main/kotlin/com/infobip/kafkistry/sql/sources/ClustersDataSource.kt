@file:Suppress("JpaDataSourceORMInspection")

package com.infobip.kafkistry.sql.sources

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

@Component
class ClustersDataSource(
    private val clustersRegistry: ClustersRegistryService,
    private val kafkaStateProvider: KafkaClustersStateProvider,
    private val brokerDiskMetricsStateProvider: BrokerDiskMetricsStateProvider,
    private val clusterIssuesInspectorService: ClusterIssuesInspectorService,
) : SqlDataSource<Cluster> {

    override fun modelAnnotatedClass(): Class<Cluster> = Cluster::class.java

    override fun supplyEntities(): List<Cluster> {
        val allClusters = clustersRegistry.listClusters()
        val allClusterStates = kafkaStateProvider.getAllLatestClusterStates()
        val brokersDiskMetrics = brokerDiskMetricsStateProvider.getAllLatestStates()
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
        diskMetricsState: StateData<ClusterBrokerMetrics>?,
        clusterIssues: List<ClusterInspectIssue>,
    ): Cluster {
        return Cluster().apply {
            cluster = kafkaCluster.identifier
            state = clusterState?.stateType ?: StateType.UNKNOWN
            usingSsl = kafkaCluster.sslEnabled
            usingSasl = kafkaCluster.saslEnabled
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
                    brokerConfigs = it.perBrokerConfig.flatMap { (broker, configs) ->
                        configs.map {
                            BrokerConfigEntry().apply {
                                brokerId = broker
                                existingEntry = it.toExistingKafkaConfigEntry()
                            }
                        }
                    }
                }
            }
            brokerDiskMetrics = diskMetricsState?.valueOrNull()?.brokersMetrics.orEmpty().map { (broker, diskMetrics) ->
                BrokerDiskMetrics().apply {
                    brokerId = broker
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
    @JoinTable(name = "Clusters_Tags")
    @Column(name = "tag")
    lateinit var tags: List<Tag>

    var metadata: ClusterMetadata? = null

    @ElementCollection
    @JoinTable(name = "Clusters_BrokerDiskMetrics")
    lateinit var brokerDiskMetrics: List<BrokerDiskMetrics>

    @ElementCollection
    @JoinTable(name = "Clusters_Issues")
    lateinit var issues: List<ClusterIssue>

}

@Embeddable
class ClusterMetadata {

    lateinit var clusterId: String
    var controllerId: Int? = null
    lateinit var connectionString: String
    lateinit var zookeeperConnectionString: String
    var clusterVersion: String? = null
    var securityEnabled: Boolean? = null

    @ElementCollection
    @JoinTable(name = "Clusters_BrokerConfigs")
    lateinit var brokerConfigs: List<BrokerConfigEntry>
}

@Embeddable
class BrokerConfigEntry {

    @Column(nullable = false)
    var brokerId: Int? = null

    lateinit var existingEntry: ExistingConfigEntry
}

@Embeddable
class BrokerDiskMetrics {

    @Column(nullable = false)
    var brokerId: Int? = null

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


