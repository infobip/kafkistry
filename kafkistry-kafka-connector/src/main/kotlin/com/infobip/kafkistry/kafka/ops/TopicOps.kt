package com.infobip.kafkistry.kafka.ops

import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.KafkaClusterManagementException
import org.apache.kafka.clients.admin.*
import org.apache.kafka.common.config.ConfigResource
import java.util.concurrent.CompletableFuture

class TopicOps(
    clientCtx: ClientCtx,
    private val clusterOps: ClusterOps,
): BaseOps(clientCtx) {

    fun listAllTopicNames(): CompletableFuture<List<TopicName>> {
        return adminClient
            .listTopics(ListTopicsOptions().listInternal(true).withReadTimeout())
            .names()
            .asCompletableFuture("list all topic names")
            .thenApply { it.sorted() }
    }

    fun listAllTopics(): CompletableFuture<List<KafkaExistingTopic>> {
        return listAllTopicNames()
            .thenCompose { topicNames ->
                val topicsPartitionDescriptionsFuture = adminClient
                    .describeTopics(topicNames, DescribeTopicsOptions().withReadTimeout())
                    .allTopicNames()
                    .asCompletableFuture("describe all topics partitions")
                val topicsConfigPropertiesFuture = adminClient
                    .describeConfigs(
                        topicNames.map { ConfigResource(ConfigResource.Type.TOPIC, it) },
                        DescribeConfigsOptions().withReadTimeout()
                    )
                    .all()
                    .asCompletableFuture("describe all topics configs")
                    .thenApply { resourceConfigs -> resourceConfigs.mapKeys { it.key.name() } }
                topicsPartitionDescriptionsFuture.thenCombine(topicsConfigPropertiesFuture) { topicsPartitionDescriptions, topicsConfigProperties ->
                    topicNames.map { topicName ->
                        val topicDescription = topicsPartitionDescriptions[topicName]
                            ?: throw KafkaClusterManagementException("Invalid response, missing topic '$topicName' in partition descriptions")
                        val topicConfig = topicsConfigProperties[topicName]
                            ?: throw KafkaClusterManagementException("Invalid response, missing topic '$topicName' in config descriptions")
                        KafkaExistingTopic(
                            name = topicName,
                            internal = topicDescription.isInternal,
                            config = topicConfig.entries()
                                .sortedBy { it.name() }
                                .associate { it.name() to it.toTopicConfigValue() },
                            partitionsAssignments = topicDescription.partitions().map { it.toPartitionAssignments() }
                        )
                    }
                }
            }
            .whenComplete { topics, _ ->
                if (topics != null) {
                    val usedReplicaBrokerIds = topics.asSequence()
                        .flatMap { it.partitionsAssignments.asSequence() }
                        .flatMap { it.replicasAssignments }
                        .map { it.brokerId }
                        .toSet()
                    clusterOps.acceptUsedReplicaBrokerIds(usedReplicaBrokerIds)
                }
            }
    }

    fun createTopic(topic: KafkaTopicConfiguration): CompletableFuture<Unit> {
        return adminClient
            .createTopics(mutableListOf(topic.toNewTopic()), CreateTopicsOptions().withWriteTimeout())
            .all()
            .asCompletableFuture("create topic")
            .thenApply { }
    }

    fun deleteTopic(topicName: TopicName): CompletableFuture<Unit> {
        return adminClient
            .deleteTopics(mutableListOf(topicName), DeleteTopicsOptions().withWriteTimeout())
            .all()
            .asCompletableFuture("delete topic")
            .thenApply { }
    }

    fun topicFullyExists(topicName: TopicName): CompletableFuture<Boolean> {
        val describeTopicOk = adminClient
            .describeTopics(mutableListOf(topicName), DescribeTopicsOptions().withReadTimeout())
            .allTopicNames().asCompletableFuture("try describe topic")
            .thenApply { true }.exceptionally { false }
        val describeTopicConfigOk = adminClient
            .describeConfigs(mutableListOf(ConfigResource(ConfigResource.Type.TOPIC, topicName)), DescribeConfigsOptions().withReadTimeout())
            .all().asCompletableFuture("try describe topic configs")
            .thenApply { true }.exceptionally { false }
        return CompletableFuture.allOf(describeTopicOk, describeTopicConfigOk)
            .thenApply { describeTopicOk.get() && describeTopicConfigOk.get() }
    }

    fun addTopicPartitions(
        topicName: TopicName, totalPartitionsCount: Int, newPartitionsAssignments: Map<Partition, List<BrokerId>>
    ): CompletableFuture<Unit> {
        val newAssignments = newPartitionsAssignments.asSequence()
            .sortedBy { it.key }
            .map { it.value }
            .toList()
        val newPartitions = NewPartitions.increaseTo(totalPartitionsCount, newAssignments)
        return adminClient
            .createPartitions(
                mapOf(topicName to newPartitions),
                CreatePartitionsOptions().withWriteTimeout()
            )
            .all()
            .asCompletableFuture("add topic partitions")
            .thenApply { }
    }

    fun describeReplicas(): CompletableFuture<List<TopicPartitionReplica>> {
        return adminClient.describeCluster(DescribeClusterOptions().withReadTimeout())
            .nodes()
            .asCompletableFuture("describe replicas all nodes")
            .thenApply { nodes -> nodes.map { it.id() } }
            .thenCompose { brokerIds ->
                adminClient
                    .describeLogDirs(brokerIds, DescribeLogDirsOptions().withReadTimeout())
                    .allDescriptions()
                    .asCompletableFuture("describe replicas log dirs")
            }
            .thenApply { brokersReplicas ->
                brokersReplicas.flatMap { (broker, dirReplicas) ->
                    dirReplicas.flatMap { (rootDir, replicas) ->
                        replicas.replicaInfos().map { (topicPartition, replica) ->
                            TopicPartitionReplica(
                                rootDir = rootDir,
                                brokerId = broker,
                                topic = topicPartition.topic(),
                                partition = topicPartition.partition(),
                                sizeBytes = replica.size(),
                                offsetLag = replica.offsetLag(),
                                isFuture = replica.isFuture
                            )
                        }
                    }
                }
            }
    }

    private fun KafkaTopicConfiguration.toNewTopic() = NewTopic(name, partitionsReplicas).apply {
        configs(config)
    }

}