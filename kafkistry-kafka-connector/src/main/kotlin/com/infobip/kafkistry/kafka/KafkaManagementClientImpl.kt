package com.infobip.kafkistry.kafka

import com.infobip.kafkistry.kafka.ops.*
import com.infobip.kafkistry.kafka.recordsampling.RecordReadSampler
import com.infobip.kafkistry.model.*
import kafka.zk.KafkaZkClient
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.*
import org.apache.kafka.common.utils.Time
import org.apache.zookeeper.client.ZKClientConfig
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

//support clusters 2.0.0 or greater

class KafkaManagementClientImpl(
    connectionDefinition: ConnectionDefinition,
    clientFactory: ClientFactory,
    readRequestTimeoutMs: Long,
    writeRequestTimeoutMs: Long,
    consumerSupplier: ClientFactory.ConsumerSupplier,
    private val recordReadSampler: RecordReadSampler,
    zookeeperConnectionResolver: ZookeeperConnectionResolver,
    eagerlyConnectToZookeeper: Boolean = false,
    controllersConnectionResolver: ControllersConnectionResolver,
) : KafkaManagementClient {

    private val currentClusterVersionRef = AtomicReference<Version?>(null)
    private val zkConnectionRef = AtomicReference<String?>(null)
    private val controllerConnectionRef = AtomicReference<String?>(null)

    private val clientCtx = BaseOps.ClientCtx(
        adminClient = clientFactory.createAdmin(connectionDefinition),
        readRequestTimeoutMs, writeRequestTimeoutMs,
        currentClusterVersionRef, zkConnectionRef,
        zkClientLazy = lazy {
            KafkaZkClient.apply(
                zkConnectionRef.get(),
                false,
                readRequestTimeoutMs.toInt(), writeRequestTimeoutMs.toInt(),
                Int.MAX_VALUE, Time.SYSTEM,
                "",
                ZKClientConfig(),
                "kr.zk.kafka.server",
                "SessionExpireListener",
                false,
            )
        },
        controllerConnectionRef,
        controllerClientLazy = lazy {
            clientFactory.createAdmin(connectionDefinition) {
                it.remove(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG)
                it[AdminClientConfig.BOOTSTRAP_CONTROLLERS_CONFIG] = controllerConnectionRef.get()
            }
        }
    )

    private val clientOps = ClientOps(clientCtx, zookeeperConnectionResolver, recordReadSampler, controllersConnectionResolver)
    private val clusterOps = ClusterOps(clientCtx)
    private val configOps = ConfigOps(clientCtx)
    private val topicOps = TopicOps(clientCtx, clusterOps)
    private val topicAssignmentsOps = TopicAssignmentsOps(clientCtx, configOps, topicOps, clusterOps)
    private val topicOffsetsOps = TopicOffsetsOps(clientCtx)
    private val aclsOps = AclsOps(clientCtx)
    private val quotasOps = QuotasOps(clientCtx)
    private val consumerGroupOps = ConsumerGroupOps(clientCtx)
    private val resetConsumerGroupOps = ResetConsumerGroupOps(
        clientCtx, consumerGroupOps, topicOffsetsOps, consumerSupplier
    )

    init {
        clientOps.bootstrapClusterVersionAndZkConnection()
        if (eagerlyConnectToZookeeper) {
            clientCtx.zkClientLazy.value.clusterId
        }
    }

    override fun close() {
        clientOps.close()
    }

    override fun test() {
        clientOps.test()
    }

    override fun clusterInfo(identifier: KafkaClusterIdentifier): CompletableFuture<ClusterInfo> {
        return clusterOps.clusterInfo(identifier)
    }

    override fun describeReplicas(): CompletableFuture<List<TopicPartitionReplica>> {
        return topicOps.describeReplicas()
    }

    override fun listReAssignments(): CompletableFuture<List<TopicPartitionReAssignment>> {
        return topicAssignmentsOps.listReAssignments()
    }

    override fun listAllTopicNames(): CompletableFuture<List<TopicName>> {
        return topicOps.listAllTopicNames()
    }

    override fun listAllTopics(): CompletableFuture<List<KafkaExistingTopic>> {
        return topicOps.listAllTopics()
    }

    override fun createTopic(topic: KafkaTopicConfiguration): CompletableFuture<Unit> {
        return topicOps.createTopic(topic)
    }

    override fun topicFullyExists(topicName: TopicName): CompletableFuture<Boolean> {
        return topicOps.topicFullyExists(topicName)
    }

    override fun updateTopicConfig(topicName: TopicName, updatingConfig: TopicConfigMap): CompletableFuture<Unit> {
        return configOps.updateTopicConfig(topicName, updatingConfig)
    }

    override fun setBrokerConfig(brokerId: BrokerId, config: Map<String, String>): CompletableFuture<Unit> {
        return configOps.setBrokerConfig(brokerId, config)
    }

    override fun unsetBrokerConfig(brokerId: BrokerId, configKeys: Set<String>): CompletableFuture<Unit> {
        return configOps.unsetBrokerConfig(brokerId, configKeys)
    }

    override fun deleteTopic(topicName: TopicName): CompletableFuture<Unit> {
        return topicOps.deleteTopic(topicName)
    }

    override fun addTopicPartitions(
        topicName: TopicName, totalPartitionsCount: Int, newPartitionsAssignments: Map<Partition, List<BrokerId>>
    ): CompletableFuture<Unit> {
        return topicOps.addTopicPartitions(topicName, totalPartitionsCount, newPartitionsAssignments)
    }

    override fun reAssignPartitions(
        topicName: TopicName, partitionsAssignments: Map<Partition, List<BrokerId>>, throttleBytesPerSec: Int
    ): CompletableFuture<Unit> = reAssignPartitions(mapOf(topicName to partitionsAssignments), throttleBytesPerSec)

    override fun reAssignPartitions(
        topicPartitionsAssignments: Map<TopicName, Map<Partition, List<BrokerId>>>, throttleBytesPerSec: Int
    ): CompletableFuture<Unit> {
        return topicAssignmentsOps.reAssignPartitions(topicPartitionsAssignments, throttleBytesPerSec)
    }

    override fun updateThrottleRate(brokerId: BrokerId, throttleRate: ThrottleRate): CompletableFuture<Unit> {
        return configOps.updateThrottleRate(brokerId, throttleRate)
    }

    override fun verifyReAssignPartitions(topicName: TopicName, partitionsAssignments: Map<Partition, List<BrokerId>>): String {
        return topicAssignmentsOps.verifyReAssignPartitions(topicName, partitionsAssignments)
    }

    override fun cancelReAssignments(topicName: TopicName, partitions: List<Partition>): CompletableFuture<Unit> {
        return topicAssignmentsOps.cancelReAssignments(topicName, partitions)
    }

    override fun runPreferredReplicaElection(topicName: TopicName, partitions: List<Partition>) {
        return topicAssignmentsOps.runPreferredReplicaElection(topicName, partitions)
    }

    override fun topicsOffsets(
        topicNames: List<TopicName>
    ): CompletableFuture<Map<TopicName, Map<Partition, PartitionOffsets>>> {
        return topicOffsetsOps.topicsOffsets(topicNames)
    }

    override fun consumerGroups(): CompletableFuture<List<ConsumerGroupId>> {
        return consumerGroupOps.consumerGroups()
    }

    override fun consumerGroup(groupId: ConsumerGroupId): CompletableFuture<ConsumerGroup> {
        return consumerGroupOps.consumerGroup(groupId)
    }

    override fun deleteConsumer(groupId: ConsumerGroupId): CompletableFuture<Unit> {
        return consumerGroupOps.deleteConsumer(groupId)
    }

    override fun deleteConsumerOffsets(
        groupId: ConsumerGroupId, topicPartitions: Map<TopicName, List<Partition>>
    ): CompletableFuture<Unit> {
        return consumerGroupOps.deleteConsumerOffsets(groupId, topicPartitions)
    }

    override fun resetConsumerGroup(
        groupId: ConsumerGroupId, reset: GroupOffsetsReset
    ): CompletableFuture<GroupOffsetResetChange> {
        return resetConsumerGroupOps.resetConsumerGroup(groupId, reset)
    }

    override fun listAcls(): CompletableFuture<List<KafkaAclRule>> {
        return aclsOps.listAcls()
    }

    override fun createAcls(acls: List<KafkaAclRule>): CompletableFuture<Unit> {
        return aclsOps.createAcls(acls)
    }

    override fun deleteAcls(acls: List<KafkaAclRule>): CompletableFuture<Unit> {
        return aclsOps.deleteAcls(acls)
    }

    override fun sampleRecords(
        topicPartitionOffsets: TopicPartitionOffsets,
        samplingPosition: SamplingPosition,
        recordVisitor: RecordVisitor,
    ): Unit = recordReadSampler.readSampleRecords(topicPartitionOffsets, samplingPosition, recordVisitor)

    override fun listQuotas(): CompletableFuture<List<ClientQuota>> {
        return quotasOps.listQuotas()
    }

    override fun setClientQuotas(quotas: List<ClientQuota>): CompletableFuture<Unit> {
        return quotasOps.setClientQuotas(quotas)
    }

    override fun removeClientQuotas(quotaEntities: List<QuotaEntity>): CompletableFuture<Unit> {
        return quotasOps.removeClientQuotas(quotaEntities)
    }

}
