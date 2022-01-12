@file:Suppress("JpaDataSourceORMInspection")

package com.infobip.kafkistry.sql.sources

import com.infobip.kafkistry.kafka.BrokerId
import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.*
import com.infobip.kafkistry.service.topic.offsets.TopicOffsets
import com.infobip.kafkistry.service.topic.offsets.TopicOffsetsService
import com.infobip.kafkistry.service.oldestrecordage.OldestRecordAgeService
import com.infobip.kafkistry.service.replicadirs.ReplicaDirsService
import com.infobip.kafkistry.service.replicadirs.TopicReplicaInfos
import com.infobip.kafkistry.service.topic.TopicsInspectionService
import com.infobip.kafkistry.service.topic.validation.rules.RuleViolation
import com.infobip.kafkistry.sql.*
import org.springframework.stereotype.Component
import java.io.Serializable
import java.util.*
import javax.persistence.*

@Component
class ClusterTopicDataSource(
    private val inspectionService: TopicsInspectionService,
    private val topicOffsetsService: TopicOffsetsService,
    private val replicaDirsService: ReplicaDirsService,
    private val oldestRecordAgeService: Optional<OldestRecordAgeService>,
) : SqlDataSource<Topic> {

    override fun modelAnnotatedClass(): Class<Topic> = Topic::class.java

    override fun supplyEntities(): List<Topic> {
        val unknownTopics = inspectionService.inspectUnknownTopics()
        val allTopicsInspections = inspectionService.inspectAllTopics()
        val allClustersTopicReplicaInfos = replicaDirsService.allClustersTopicReplicaInfos()
        val allClustersTopicOldestAges = oldestRecordAgeService.orElse(null)
            ?.allClustersTopicOldestRecordAges().orEmpty()
        return (allTopicsInspections + unknownTopics).flatMap { topicStatuses ->
            val topicName = topicStatuses.topicName
            topicStatuses.statusPerClusters.map {
                val topicOffsets = topicOffsetsService.topicOffsets(it.clusterIdentifier, topicName)
                val replicaInfos = allClustersTopicReplicaInfos[it.clusterIdentifier]?.get(topicName)
                val oldestRecordAges = allClustersTopicOldestAges[it.clusterIdentifier]?.get(topicName)
                mapClusterTopic(topicName, topicStatuses.topicDescription, it, topicOffsets, replicaInfos, oldestRecordAges)
            }
        }
    }

    private fun mapClusterTopic(
        topicName: TopicName,
        topicDescription: TopicDescription?,
        topicClusterStatus: TopicClusterStatus,
        topicOffsets: TopicOffsets?,
        topicReplicas: TopicReplicaInfos?,
        oldestRecordAges: Map<Partition, Long>?,
    ): Topic {
        val clusterRef = ClusterRef(
            topicClusterStatus.clusterIdentifier, topicClusterStatus.clusterTags
        )
        return Topic().apply {
            id = ClusterTopicId().apply {
                topic = topicName
                cluster = clusterRef.identifier
            }
            exist = topicClusterStatus.status.exists
            shouldExist = topicDescription?.presence?.needToBeOnCluster(clusterRef) ?: false
            val wrongValueStatuses = topicClusterStatus.status.wrongValues?.map { wrongValue ->
                TopicOnClusterStatus().apply {
                    type = wrongValue.type
                    issueCategory = wrongValue.type.category
                    expected = wrongValue.expected
                    expectedDefault = wrongValue.expectedDefault
                    actual = wrongValue.actual
                    message = if (wrongValue.expectedDefault) {
                        "Expecting server's default but having non-default actual value: '$actual'"
                    } else {
                        "Expected value '$expected', actual value: '$actual'"
                    }
                }
            } ?: emptyList()
            val ruleViolations = sequence {
                topicClusterStatus.status.ruleViolations?.also { yieldAll(it) }
                topicClusterStatus.status.currentConfigRuleViolations?.also { yieldAll(it) }
            }.map { ruleViolation ->
                TopicOnClusterStatus().apply {
                    type = ruleViolation.type
                    issueCategory = ruleViolation.type.category
                    message = ruleViolation.renderMessage()
                    ruleClassName = ruleViolation.ruleClassName
                    severity = ruleViolation.severity
                }
            }.toList()
            val otherStatuses = topicClusterStatus.status.types
                .filter { type -> wrongValueStatuses.all { it.type != type } && ruleViolations.all { it.type != type } }
                .map {
                    TopicOnClusterStatus().apply {
                        type = it
                        issueCategory = it.category
                    }
                }
            statuses = wrongValueStatuses + ruleViolations + otherStatuses
            if (topicDescription != null && topicDescription.presence.needToBeOnCluster(clusterRef)) {
                val expectedProperties = topicDescription.propertiesForCluster(clusterRef)
                expectedPartitionCount = expectedProperties.partitionCount
                expectedReplicationFactor = expectedProperties.replicationFactor
                expectedConfig = topicDescription.configForCluster(clusterRef).map {
                    it.toKafkaConfigEntry()
                }
            }
            val existingTopicInfo = topicClusterStatus.existingTopicInfo
            if (existingTopicInfo != null) {
                actualPartitionCount = existingTopicInfo.properties.partitionCount
                actualReplicationFactor = existingTopicInfo.properties.replicationFactor
                actualConfig = existingTopicInfo.config.map {
                    it.toExistingKafkaConfigEntry()
                }
                val usedReplicas = mutableSetOf<Pair<Partition, BrokerId>>()
                val assignedReplicas =
                    existingTopicInfo.partitionsAssignments.flatMap { partitionAssignments ->
                        partitionAssignments.replicasAssignments.mapIndexed { index, replica ->
                            val replicaInfos = topicReplicas?.partitionBrokerReplicas
                                ?.get(partitionAssignments.partition)
                                ?.get(replica.brokerId)
                            usedReplicas.add(partitionAssignments.partition to replica.brokerId)
                            PartitionBrokerReplica().apply {
                                brokerId = replica.brokerId
                                partition = partitionAssignments.partition
                                orphan = false
                                rank = index
                                inSync = replica.inSyncReplica
                                leader = replica.leader
                                dir = replicaInfos?.rootDir
                                sizeBytes = replicaInfos?.sizeBytes
                                offsetLag = replicaInfos?.offsetLag
                                isFuture = replicaInfos?.isFuture
                            }
                        }
                    }
                val orphanReplicas = topicReplicas?.partitionBrokerReplicas?.values
                    ?.flatMap { it.values }
                    ?.filter { (it.partition to it.brokerId) !in usedReplicas }
                    ?.map { replicaInfos ->
                        PartitionBrokerReplica().apply {
                            brokerId = replicaInfos.brokerId
                            partition = replicaInfos.partition
                            orphan = true
                            rank = null
                            inSync = null
                            leader = null
                            dir = replicaInfos.rootDir
                            sizeBytes = replicaInfos.sizeBytes
                            offsetLag = replicaInfos.offsetLag
                            isFuture = replicaInfos.isFuture
                        }
                    }
                    ?: emptyList()
                replicas = assignedReplicas + orphanReplicas
            }
            if (topicOffsets != null) {
                partitions = topicOffsets.partitionsOffsets.map { (p, offsets) ->
                    TopicPartition().apply {
                        partition = p
                        begin = offsets.begin
                        end = offsets.end
                        count = offsets.end - offsets.begin
                        producerRate = topicOffsets.partitionMessageRate[p]?.upTo15MinRate
                        producerDayAvgRate = topicOffsets.partitionMessageRate[p]?.upTo24HRate
                        oldestRecordAgeMs = oldestRecordAges?.get(p)
                    }
                }
                numMessages = topicOffsets.size
                producerRate = ProducerRate().apply {
                    producerRateLast15Sec = topicOffsets.messagesRate?.last15Sec
                    producerRateLastMin = topicOffsets.messagesRate?.lastMin
                    producerRateLast5Min = topicOffsets.messagesRate?.last5Min
                    producerRateLast15Min = topicOffsets.messagesRate?.last15Min
                    producerRateLast30Min = topicOffsets.messagesRate?.last30Min
                    producerRateLastHour = topicOffsets.messagesRate?.lastH
                    producerRateLast2Hours = topicOffsets.messagesRate?.last2H
                    producerRateLast6Hours = topicOffsets.messagesRate?.last6H
                    producerRateLast12Hours = topicOffsets.messagesRate?.last12H
                    producerRateLast24Hours = topicOffsets.messagesRate?.last24H
                }
            }
            topicClusterStatus.resourceRequiredUsages.value?.also { usages ->
                requiredResourceUsage = RequiredExpectedUsage().apply {
                    expectedNumBrokers = usages.numBrokers
                    expectedMessagesPerSec = usages.messagesPerSec
                    expectedBytesPerSec = usages.bytesPerSec
                    expectedProducedBytesPerDay = usages.producedBytesPerDay
                    expectedDiskUsagePerPartitionReplica = usages.diskUsagePerPartitionReplica
                    expectedDiskUsagePerBroker = usages.diskUsagePerBroker
                    expectedTotalDiskUsageBytes = usages.totalDiskUsageBytes
                    expectedPartitionInBytesPerSec = usages.partitionInBytesPerSec
                    expectedPartitionSyncOutBytesPerSec = usages.partitionSyncOutBytesPerSec
                    expectedBrokerProducerInBytesPerSec = usages.brokerProducerInBytesPerSec
                    expectedBrokerSyncBytesPerSec = usages.brokerSyncBytesPerSec
                    expectedBrokerInBytesPerSec = usages.brokerInBytesPerSec
                }
            }
        }
    }


}

@Embeddable
class ClusterTopicId : Serializable {

    lateinit var cluster: KafkaClusterIdentifier
    lateinit var topic: TopicName
}

@Entity
@Table(name = "Topics")
class Topic {

    @EmbeddedId
    lateinit var id: ClusterTopicId

    var exist: Boolean? = null

    @Column(nullable = false)
    var shouldExist: Boolean? = null

    @ElementCollection
    @JoinTable(name = "Topics_Statuses")
    lateinit var statuses: List<TopicOnClusterStatus>

    var expectedPartitionCount: Int? = null
    var expectedReplicationFactor: Int? = null
    var actualPartitionCount: Int? = null
    var actualReplicationFactor: Int? = null

    @ElementCollection
    @JoinTable(name = "Topics_ExpectedConfigs")
    lateinit var expectedConfig: List<KafkaConfigEntry>

    @ElementCollection
    @JoinTable(name = "Topics_ActualConfigs")
    lateinit var actualConfig: List<ExistingConfigEntry>

    @ElementCollection
    @JoinTable(name = "Topics_Replicas")
    lateinit var replicas: List<PartitionBrokerReplica>

    @ElementCollection
    @JoinTable(name = "Topics_Partitions")
    lateinit var partitions: List<TopicPartition>
    var numMessages: Long? = null

    @Embedded
    lateinit var producerRate: ProducerRate

    @Embedded
    var requiredResourceUsage: RequiredExpectedUsage? = null
}

@Embeddable
class TopicOnClusterStatus {

    @Enumerated(EnumType.STRING)
    lateinit var type: InspectionResultType

    @Enumerated(EnumType.STRING)
    lateinit var issueCategory: IssueCategory

    lateinit var message: String

    //for wrong value
    var expected: String? = null
    var expectedDefault: Boolean? = null
    var actual: String? = null

    //for rule violations
    var ruleClassName: String? = null
    @Enumerated(EnumType.STRING)
    var severity: RuleViolation.Severity? = null
}

@Embeddable
class ProducerRate {
    var producerRateLast15Sec: Double? = null
    var producerRateLastMin: Double? = null
    var producerRateLast5Min: Double? = null
    var producerRateLast15Min: Double? = null
    var producerRateLast30Min: Double? = null
    var producerRateLastHour: Double? = null
    var producerRateLast2Hours: Double? = null
    var producerRateLast6Hours: Double? = null
    var producerRateLast12Hours: Double? = null
    var producerRateLast24Hours: Double? = null
}

@Embeddable
class PartitionBrokerReplica {
    @Column(nullable = false)
    var brokerId: Int? = null

    @Column(nullable = false)
    var partition: Int? = null

    @Column(nullable = false)
    var orphan: Boolean? = null

    var rank: Int? = null

    var inSync: Boolean? = null

    var leader: Boolean? = null

    var dir: String? = null
    var sizeBytes: Long? = null
    var offsetLag: Long? = null
    var isFuture: Boolean? = null
}

@Embeddable
class TopicPartition {

    @Column(nullable = false)
    var partition: Int? = null

    @Column(nullable = false)
    var begin: Long? = null

    @Column(nullable = false)
    var end: Long? = null

    var count: Long? = null

    var producerRate: Double? = null
    var producerDayAvgRate: Double? = null

    var oldestRecordAgeMs: Long? = null
}

@Embeddable
class RequiredExpectedUsage {
    var expectedNumBrokers: Int? = null
    var expectedMessagesPerSec: Double? = null
    var expectedBytesPerSec: Double? = null
    var expectedProducedBytesPerDay: Long? = null
    var expectedDiskUsagePerPartitionReplica: Long? = null
    var expectedDiskUsagePerBroker: Long? = null
    var expectedTotalDiskUsageBytes: Long? = null
    var expectedPartitionInBytesPerSec: Double? = null
    var expectedPartitionSyncOutBytesPerSec: Double? = null
    var expectedBrokerProducerInBytesPerSec: Double? = null
    var expectedBrokerSyncBytesPerSec: Double? = null
    var expectedBrokerInBytesPerSec: Double? = null
}
