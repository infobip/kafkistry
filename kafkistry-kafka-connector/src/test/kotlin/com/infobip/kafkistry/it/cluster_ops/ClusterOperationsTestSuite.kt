package com.infobip.kafkistry.it.cluster_ops

import org.apache.kafka.clients.admin.ConfigEntry
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.InvalidRequestException
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.*
import org.awaitility.Awaitility
import com.infobip.kafkistry.kafka.*
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.QuotaEntity
import com.infobip.kafkistry.model.QuotaProperties
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.KafkaClusterManagementException
import org.apache.kafka.common.config.TopicConfig
import org.assertj.core.api.SoftAssertions
import org.assertj.core.api.StringAssert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.collections.MutableMap.MutableEntry

abstract class ClusterOperationsTestSuite : AbstractClusterOpsTestSuite() {

    abstract val expectedClusterVersion: Version
    abstract val expectedKraftEnabled: Boolean

    private fun entry(key: String, value: String): MutableEntry<String, ConfigValue> = object : MutableEntry<String, ConfigValue> {
        override val key: String
            get() = key
        override val value: ConfigValue
            get() = ConfigValue(value, default = false, readOnly = false, sensitive = false, ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG)

        override fun setValue(newValue: ConfigValue) = throw UnsupportedOperationException()
        override fun toString() = "$key=$value"
    }

    @Test
    fun `test create topic`() {
        val topics = doOnKafka {
            it.createTopic(KafkaTopicConfiguration(
                    name = "create-test-topic",
                    partitionsReplicas = createAssignments(3, 2),
                    config = mapOf("retention.bytes" to "123456789")
            )).get()
            it.awaitTopicCreated("create-test-topic")
            it.listAllTopics().get()
        }
        assertThat(topics).hasSize(1)
        val existingTopic = topics[0]
        assertThat(existingTopic.name).isEqualTo("create-test-topic")
        assertThat(existingTopic.partitionsAssignments).hasSize(3)
                .extracting<Int> { it.replicasAssignments.size }
                .containsOnly(2)    //replication factor
        assertThat(existingTopic.config.filterValues { !it.default }.filterOutFalseDefaults())
                .containsOnly(
                        entry("retention.bytes", "123456789")
                )
    }

    @Test
    fun `test create topic with replication factor higher than num brokers`() {
        try {
            doOnKafka {
                it.createTopic(KafkaTopicConfiguration(
                        name = "create-test-topic",
                        partitionsReplicas = createAssignments(1, 4),
                        config = mapOf()
                )).get()
            }
            fail<Unit>("Expected to get a KafkaClusterManagementException")
        } catch (e: ExecutionException) {
            assertThat(e.cause).isInstanceOf(KafkaClusterManagementException::class.java)
        }
    }

    @Test
    fun `test delete topic`() {
        doOnKafka {
            it.createTopic(KafkaTopicConfiguration(
                name = "delete-test-topic",
                partitionsReplicas = createAssignments(1, 1),
                config = emptyMap(),
            )).get()
            it.awaitTopicCreated("delete-test-topic")
            it.deleteTopic("delete-test-topic").get()
        }
        Awaitility.await("deletion to complete")
            .atMost(Duration.ofSeconds(5))
            .untilAsserted {
                val topics = try {
                    doOnKafka { it.listAllTopics().get() }
                } catch (ex: Throwable) {
                    fail("failed to list topics", ex)
                }
                assertThat(topics).isEmpty()
            }
    }

    @Test
    fun `test change topic config`() {
        doOnKafka {
            it.createTopic(KafkaTopicConfiguration(
                    name = "update-test-topic",
                    partitionsReplicas = createAssignments(1, 3),
                    config = mapOf("retention.bytes" to "111222333", "retention.ms" to "3600")
            )).get()
            it.awaitTopicCreated("update-test-topic")
            it.updateTopicConfig("update-test-topic", mapOf(
                    "retention.bytes" to "444555666",
                    "min.insync.replicas" to "2"
            )).get()
            it.listAllTopics().get()
        }
        Awaitility.await("for topic update to be applied")
            .atMost(Duration.ofSeconds(5))
            .untilAsserted {
                val topics = doOnKafka { it.listAllTopics().get() }.filter { !it.internal }
                assertThat(topics).hasSize(1)
                val existingTopic = topics[0]
                assertThat(existingTopic.name).isEqualTo("update-test-topic")
                assertThat(existingTopic.partitionsAssignments).hasSize(1)
                    .extracting<Int> { it.replicasAssignments.size }
                    .containsOnly(3)    //replication factor
                assertThat(existingTopic.config.filterValues { !it.default }.filterOutFalseDefaults())
                    .contains(
                        entry("retention.bytes", "444555666"),
                        entry("min.insync.replicas", "2")
                    )
                    .doesNotContainKey("retention.ms")
            }
    }

    @Test
    fun `test add topic partition(s)`() {
        doOnKafka {
            it.createTopic(KafkaTopicConfiguration(
                    name = "partition-test-topic",
                    partitionsReplicas = createAssignments(2, 2),
                    config = emptyMap()
            )).get()
            it.awaitTopicCreated("partition-test-topic")
            it.addTopicPartitions("partition-test-topic", 4, mapOf(
                    2 to listOf(0, 1),
                    3 to listOf(1, 2)
            )).get()
        }
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted {
                    val topics = doOnKafka { it.listAllTopics().get() }
                    assertThat(topics).hasSize(1)
                    val existingTopic = topics[0]
                    assertThat(existingTopic.name).isEqualTo("partition-test-topic")
                    assertThat(existingTopic.partitionsAssignments)
                            .hasSize(4)
                            .extracting<Int> { it.replicasAssignments.size }
                            .containsOnly(2)    //replication factor
                    assertThat(existingTopic.partitionsAssignments[0].replicasAssignments.map { it.brokerId }).allMatch { it in 0..2 }  //assigned by kafka
                    assertThat(existingTopic.partitionsAssignments[1].replicasAssignments.map { it.brokerId }).allMatch { it in 0..2 }  //assigned by kafka
                    assertThat(existingTopic.partitionsAssignments[2].replicasAssignments.map { it.brokerId }).containsExactly(0, 1)  //assigned by update
                    assertThat(existingTopic.partitionsAssignments[3].replicasAssignments.map { it.brokerId }).containsExactly(1, 2)  //assigned by update
                }
    }

    @Test
    fun `test increase replication factor`() {
        val topics = doOnKafka {
            it.createTopic(KafkaTopicConfiguration(
                    name = "incr-rep-factor-test-topic",
                    partitionsReplicas = createAssignments(2, 1),
                    config = emptyMap()
            )).get()
            it.awaitTopicCreated("incr-rep-factor-test-topic")
            it.listAllTopics().get()
        }
        assertThat(topics).hasSize(1)
        val existingTopic = topics[0]
        assertThat(existingTopic.name).isEqualTo("incr-rep-factor-test-topic")
        assertThat(existingTopic.partitionsAssignments)
                .hasSize(2)
                .extracting<Int> { it.replicasAssignments.size }
                .containsOnly(1)    //replication factor before

        val newAssignments = createAssignments(2, 3)
        doOnKafka {
            it.reAssignPartitions("incr-rep-factor-test-topic", newAssignments, 1024).get()
        }
        Awaitility.await("for new replicas being added")
                .atMost(15, TimeUnit.SECONDS)
                .untilAsserted {
                    val topicsAfter = doOnKafka { it.listAllTopics().get() }
                    assertThat(topicsAfter).hasSize(1)
                    val existingTopicAfter = topicsAfter[0]
                    assertThat(existingTopicAfter.name).isEqualTo("incr-rep-factor-test-topic")
                    assertThat(existingTopicAfter.partitionsAssignments)
                            .hasSize(2)
                            .extracting<Int> { it.replicasAssignments.size }
                            .containsOnly(3)    //replication factor after
                    assertThat(existingTopicAfter.partitionsAssignments.toPartitionReplicasMap()).isEqualTo(newAssignments)
                }
        verifyReAssignment("incr-rep-factor-test-topic", newAssignments)
    }

    @Test
    fun `test reduce replication factor`() {
        val topics = doOnKafka {
            it.createTopic(KafkaTopicConfiguration(
                    name = "decr-rep-factor-test-topic",
                    partitionsReplicas = createAssignments(2, 3),
                    config = emptyMap()
            )).get()
            it.awaitTopicCreated("decr-rep-factor-test-topic")
            it.listAllTopics().get()
        }
        assertThat(topics).hasSize(1)
        val existingTopic = topics[0]
        assertThat(existingTopic.name).isEqualTo("decr-rep-factor-test-topic")
        assertThat(existingTopic.partitionsAssignments)
                .hasSize(2)
                .extracting<Int> { it.replicasAssignments.size }
                .containsOnly(3)    //replication factor before

        val newAssignments = existingTopic.partitionsAssignments.toPartitionReplicasMap()
            .reduceReplicationFactor(1)
        doOnKafka {
            it.reAssignPartitions("decr-rep-factor-test-topic", newAssignments, 0).get()
        }
        Awaitility.await("for replicas being removed")
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted {
                    val topicsAfter = doOnKafka { it.listAllTopics().get() }
                    assertThat(topicsAfter).hasSize(1)
                    val existingTopicAfter = topicsAfter[0]
                    assertThat(existingTopicAfter.name).isEqualTo("decr-rep-factor-test-topic")
                    assertThat(existingTopicAfter.partitionsAssignments)
                            .hasSize(2)
                            .extracting<Int> { it.replicasAssignments.size }
                            .containsOnly(1)    //replication factor after
                    assertThat(existingTopicAfter.partitionsAssignments.toPartitionReplicasMap()).isEqualTo(newAssignments)
                }
        //no need for verify since here was no throttle
    }

    @Test
    fun `test re-assign partition replicas`() {
        val topics = doOnKafka {
            it.createTopic(KafkaTopicConfiguration(
                    name = "re-assign-partition-replicas-test-topic",
                    partitionsReplicas = createAssignments(3, 2),
                    config = emptyMap()
            )).get()
            it.awaitTopicCreated("re-assign-partition-replicas-test-topic")
            it.listAllTopics().get()
        }
        assertThat(topics).hasSize(1)
        val existingTopic = topics[0]
        assertThat(existingTopic.name).isEqualTo("re-assign-partition-replicas-test-topic")
        assertThat(existingTopic.partitionsAssignments)
                .hasSize(3)
                .extracting<Int> { it.replicasAssignments.size }
                .containsOnly(2)    //replication factor

        val newAssignments = mapOf(
                0 to listOf(0, 1),
                1 to listOf(0, 1),
                2 to listOf(0, 1)
        )
        doOnKafka {
            it.reAssignPartitions("re-assign-partition-replicas-test-topic", newAssignments, 1024).get()
        }
        verifyReAssignment("re-assign-partition-replicas-test-topic", newAssignments)
        val topicsAfter = doOnKafka { it.listAllTopics().get() }
        assertThat(topicsAfter).hasSize(1)
        val existingTopicAfter = topicsAfter[0]
        assertThat(existingTopicAfter.name).isEqualTo("re-assign-partition-replicas-test-topic")
        assertThat(existingTopicAfter.partitionsAssignments.toPartitionReplicasMap()).isEqualTo(newAssignments)
        assertThat(existingTopicAfter.config.mapValues { it.value.value }).`as`("Topic throttle removed").contains(
                Assertions.entry("follower.replication.throttled.replicas", ""),
                Assertions.entry("leader.replication.throttled.replicas", ""),
        )
    }

    @Test
    fun `test invalid re-assignment`() {
        val initialAssignments = mapOf(
            0 to listOf(0, 1),
            1 to listOf(1, 2),
            2 to listOf(2, 0),
        )
        val topics = doOnKafka {
            it.createTopic(KafkaTopicConfiguration(
                name = "invalid-re-assign-partition-replicas-test-topic",
                partitionsReplicas = initialAssignments,
                config = emptyMap()
            )).get()
            it.awaitTopicCreated("invalid-re-assign-partition-replicas-test-topic")
            it.listAllTopics().get()
        }
        assertThat(topics).hasSize(1)
        val existingTopic = topics[0]
        assertThat(existingTopic.name).isEqualTo("invalid-re-assign-partition-replicas-test-topic")

        val newAssignmentsInvalidBroker = mapOf(
            0 to listOf(3, 1),
            1 to listOf(1, 4),
            2 to listOf(5, 0),
        )
        assertThatThrownBy {
            doOnKafka {
                it.reAssignPartitions("invalid-re-assign-partition-replicas-test-topic", newAssignmentsInvalidBroker, 1024).get()
            }
        }.isInstanceOf(KafkaClusterManagementException::class.java)
            .hasMessageContaining("verify reassignments used brokers")
            .rootCause()
            .isInstanceOf(KafkaClusterManagementException::class.java)
            .hasMessageContaining("Unknown broker(s) used in assignments")

        val newAssignmentsInvalidPartitions = mapOf(
            0 to listOf(0, 1),
            1 to listOf(1, 2),
            3 to listOf(2, 0),
        )
        assertThatThrownBy {
            doOnKafka {
                it.reAssignPartitions("invalid-re-assign-partition-replicas-test-topic", newAssignmentsInvalidPartitions, 1024).get()
            }
        }.isInstanceOf(KafkaClusterManagementException::class.java)
            .hasMessageContaining("verify reassignments partitions")
            .rootCause()
            .isInstanceOf(KafkaClusterManagementException::class.java)
            .hasMessageContaining("Trying to reassign non-existent topic partitions")

        val topicsAfter = doOnKafka { it.listAllTopics().get() }
        assertThat(topicsAfter).hasSize(1)
        val existingTopicAfter = topicsAfter[0]
        assertThat(existingTopicAfter.name).isEqualTo("invalid-re-assign-partition-replicas-test-topic")
        assertThat(existingTopicAfter.partitionsAssignments.toPartitionReplicasMap()).isEqualTo(initialAssignments)
    }

    @Test
    fun `test elect preferred leader replica`() {
        doOnKafka {
            it.createTopic(KafkaTopicConfiguration(
                    name = "elect-leader-test-topic",
                    partitionsReplicas = createAssignments(3, 2),
                    config = emptyMap()
            )).get()
            it.awaitTopicCreated("elect-leader-test-topic")
        }
        val preAssignments = mapOf(
                0 to listOf(1, 0),
                1 to listOf(2, 1),
                2 to listOf(2, 0)
        )
        doOnKafka {
            it.reAssignPartitions("elect-leader-test-topic", preAssignments, 1024).get()
            it.runPreferredReplicaElection("elect-leader-test-topic", listOf(0, 1))
        }
        verifyReAssignment("elect-leader-test-topic", preAssignments)

        assertThat(doOnKafka { it.listAllTopics().get() }[0].partitionsAssignments.partitionLeadersMap()).isEqualTo(mapOf(
                0 to 1, 1 to 2, 2 to 2
        ))
        val newAssignments = mapOf(
                0 to listOf(0, 1),
                1 to listOf(1, 2),
                2 to listOf(2, 0)
        )
        doOnKafka {
            it.reAssignPartitions("elect-leader-test-topic", newAssignments, 1024).get()
        }
        fun partitionLeadersMap(): Map<Partition, BrokerId> {
            return doOnKafka { it.listAllTopics().get() }
                .first { it.name == "elect-leader-test-topic" }
                .partitionsAssignments
                .partitionLeadersMap()
        }
        assertThat(partitionLeadersMap()).isEqualTo(mapOf(
            0 to 1, 1 to 2, 2 to 2
        ))
        verifyReAssignment("elect-leader-test-topic", newAssignments)
        assertThat(partitionLeadersMap()).isEqualTo(mapOf(
            0 to 1, 1 to 2, 2 to 2
        ))

        val existingTopicBefore = doOnKafka { it.listAllTopics().get() }[0]
        assertThat(existingTopicBefore.name).isEqualTo("elect-leader-test-topic")
        assertThat(existingTopicBefore.partitionsAssignments.toPartitionReplicasMap()).isEqualTo(newAssignments)
        assertThat(existingTopicBefore.partitionsAssignments.partitionLeadersMap()).isEqualTo(mapOf(
                0 to 1,
                1 to 2,
                2 to 2
        ))

        doOnKafka {
            it.runPreferredReplicaElection("elect-leader-test-topic", listOf(0, 1))
        }
        Awaitility.await("for election to coplete")
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted {
                    val existingTopicAfter = doOnKafka { it.listAllTopics().get() }[0]
                    assertThat(existingTopicAfter.name).isEqualTo("elect-leader-test-topic")
                    assertThat(existingTopicAfter.partitionsAssignments.toPartitionReplicasMap()).isEqualTo(newAssignments)
                    assertThat(existingTopicAfter.partitionsAssignments.partitionLeadersMap()).isEqualTo(mapOf(
                            0 to 0,
                            1 to 1,
                            2 to 2
                    ))
                }
    }

    @Test
    fun `test cluster info`() {
        val clusterInfo = doOnKafka { it.clusterInfo("test-id").get() }
        assertThat(clusterInfo.clusterVersion).isEqualTo(expectedClusterVersion)
        assertThat(clusterInfo.nodeIds).hasSize(3)
        assertThat(clusterInfo.kraftEnabled).`as`("kraft enabled").isEqualTo(expectedKraftEnabled)
        if (expectedKraftEnabled) {
            assertThat(clusterInfo.quorumInfo.voters).hasSize(3)
            assertThat(clusterInfo.quorumInfo.observers).hasSize(0)
        } else {
            assertThat(clusterInfo.quorumInfo).isEqualTo(ClusterQuorumInfo.EMPTY)
        }
    }

    @Test
    fun `read topic offsets`() {
        val topic = "read-offsets-test-topic"
        doOnKafka {
            it.createTopic(KafkaTopicConfiguration(
                    name = topic,
                    partitionsReplicas = createAssignments(2, 3),
                    config = emptyMap()
            )).get()
            it.awaitTopicCreated(topic)
        }

        //check offsets before producing
        val topicsOffsetsBefore: Map<TopicName, Map<Partition, PartitionOffsets>> = doOnKafka {
            it.topicsOffsets(listOf(topic)).get()
        }
        assertThat(topicsOffsetsBefore).isEqualTo(mapOf(topic to mapOf(
                0 to PartitionOffsets(0L, 0L),
                1 to PartitionOffsets(0L, 0L)
        )))

        //produce messages
        val f1 = producer.send(ProducerRecord(topic, 0, "k1", "v1".toByteArray()))
        val f2 = producer.send(ProducerRecord(topic, 1, "k2", "v2".toByteArray()))
        val f3 = producer.send(ProducerRecord(topic, 0, "k3", "v3".toByteArray()))
        producer.flush()
        listOf(f1, f2, f3).forEach { it.get(2, TimeUnit.SECONDS) }

        //check offsets after producing
        val topicsOffsetsAfter: Map<TopicName, Map<Partition, PartitionOffsets>> = doOnKafka {
            it.topicsOffsets(listOf(topic)).get()
        }
        assertThat(topicsOffsetsAfter).isEqualTo(mapOf(topic to mapOf(
                0 to PartitionOffsets(0L, 2L),
                1 to PartitionOffsets(0L, 1L)
        )))
    }

    @Test
    fun `test get consumer groups - empty`() {
        val groups = doOnKafka { it.consumerGroups().get() }
        assertThat(groups).isEmpty()
    }

    @Test
    fun `test get assigned consumer group info`() {
        doOnKafka {
            it.createTopic(KafkaTopicConfiguration(
                    name = "to-read-test",
                    partitionsReplicas = createAssignments(3, 2),
                    config = emptyMap()
            )).get()
            it.awaitTopicCreated("to-read-test")
        }
        val consumer = createConsumerAndSubscribe("test-consumer", "to-read-test")
        Awaitility.await("group to get assignments")
            .atMost(Duration.ofSeconds(6))
            .untilAsserted {
                consumer.poll(Duration.ZERO)
                assertThat(consumer.assignment()).containsExactlyInAnyOrder(
                    TopicPartition("to-read-test", 0),
                    TopicPartition("to-read-test", 1),
                    TopicPartition("to-read-test", 2),
                )
            }

        //check group is now listed
        val groups = doOnKafka { it.consumerGroups().get() }
        assertThat(groups).containsExactly("test-consumer")

        //check assignments and offsets before commit
        val groupBeforeCommit = doOnKafka { it.consumerGroup("test-consumer").get() }
        log.info("group: ${groupBeforeCommit.id} -> ${groupBeforeCommit.status} ${groupBeforeCommit.members}" )
        assertThat(groupBeforeCommit.id).isEqualTo("test-consumer")
        assertThat(groupBeforeCommit.status).isEqualTo(ConsumerGroupStatus.STABLE)
        assertThat(groupBeforeCommit.members).isNotEmpty
        assertThat(groupBeforeCommit.offsets).isEmpty() //nothing committed yet
        assertThat(groupBeforeCommit.assignments).`as`("All partitions assigned")
            .extracting<List<Any?>> { listOf(it.topic, it.partition) }
            .containsExactlyInAnyOrder(
                listOf("to-read-test", 0),
                listOf("to-read-test", 1),
                listOf("to-read-test", 2),
            )

        Awaitility.await("group assignment must still be 3 partitions")
            .atMost(Duration.ofSeconds(5))
            .untilAsserted {
                consumer.poll(Duration.ZERO)
                assertThat(consumer.assignment()).hasSize(3)
            }

        //do commit for all assignment partitions with current (start offsets) position
        consumer.commitSync(Duration.ofSeconds(2))

        Awaitility.await("group assignment must still be 3 partitions")
            .atMost(Duration.ofSeconds(5))
            .untilAsserted {
                consumer.poll(Duration.ZERO)
                assertThat(consumer.assignment()).hasSize(3)
            }

        //check offsets after commit
        Awaitility.await("commits to be visible")
            .atMost(Duration.ofSeconds(10))
            .untilAsserted {
                consumer.commitSync(Duration.ofSeconds(2))
                val groupAfterCommit = doOnKafka { it.consumerGroup("test-consumer").get() }
                assertThat(groupAfterCommit.offsets)
                    .extracting<List<Any?>> { listOf(it.topic, it.partition, it.offset) }
                    .containsExactlyInAnyOrder(
                        listOf("to-read-test", 0, 0L),
                        listOf("to-read-test", 1, 0L),
                        listOf("to-read-test", 2, 0L),
                    )
            }

        val groupAfterCommit = doOnKafka { it.consumerGroup("test-consumer").get() }
        assertThat(groupBeforeCommit.assignments).`as`("All partitions still assigned")
            .extracting<List<Any?>> { listOf(it.topic, it.partition) }
            .containsExactlyInAnyOrder(
                listOf("to-read-test", 0),
                listOf("to-read-test", 1),
                listOf("to-read-test", 2),
            )
        assertThat(groupBeforeCommit.assignments)
            .`as`("All partitions assignments are same as before commit")
            .isEqualTo(groupAfterCommit.assignments)

        //check empty after
        consumer.close(Duration.ofSeconds(2))
        Awaitility.await("empty after unsubscribe")
            .atMost(Duration.ofSeconds(5))
            .untilAsserted {
                val groupAfterClose = doOnKafka { it.consumerGroup("test-consumer").get() }
                assertThat(groupAfterClose.status).isEqualTo(ConsumerGroupStatus.EMPTY)
                assertThat(groupAfterClose.members).isEmpty()
                assertThat(groupAfterClose.offsets)
                    .`as`("Even though group is empty, committed offsets are still there")
                    .extracting<List<Any?>> { listOf(it.topic, it.partition, it.offset) }
                    .containsExactlyInAnyOrder(
                        listOf("to-read-test", 0, 0L),
                        listOf("to-read-test", 1, 0L),
                        listOf("to-read-test", 2, 0L)
                    )
                assertThat(groupAfterClose.assignments).isEmpty()
            }

        //check deletion of specific topic-partition offsets
        if (expectedClusterVersion >= Version.of("2.4")) {
            doOnKafka { it.deleteConsumerOffsets("test-consumer", mapOf("to-read-test" to listOf(1))).get() }
            val groupAfterOffsetsDeletion = doOnKafka { it.consumerGroup("test-consumer").get() }
            assertThat(groupAfterOffsetsDeletion.status).isEqualTo(ConsumerGroupStatus.EMPTY)
            assertThat(groupAfterOffsetsDeletion.members).isEmpty()
            assertThat(groupAfterOffsetsDeletion.offsets)
                .`as`("Only offset for partition 1 is deleted")
                .extracting<List<Any?>> { listOf(it.topic, it.partition, it.offset) }
                .containsExactlyInAnyOrder(
                    listOf("to-read-test", 0, 0L),
                    listOf("to-read-test", 2, 0L)
                )
        }

        //check not listed after deletion
        doOnKafka { it.deleteConsumer("test-consumer").get() }
        val groupsAfterDelete = doOnKafka { it.consumerGroups().get() }
        assertThat(groupsAfterDelete).isEmpty()
    }

    @Test
    fun `test reset consumer group offsets`() {
        val topic = "to-reset-test"
        val groupId = "test-reset-consumer"
        doOnKafka {
            it.createTopic(KafkaTopicConfiguration(
                    name = topic,
                    partitionsReplicas = createAssignments(3, 2),
                    config = emptyMap()
            )).get()
            it.awaitTopicCreated(topic)
        }

        //produce messages
        fun produceIntoPartition(partition: Int): Future<*> {
            return producer.send(ProducerRecord(topic, partition, "key", "value".toByteArray()))
        }

        val futures1 = (0..9).map { produceIntoPartition(0) }
        val futures2 = (0..4).map { produceIntoPartition(1) }
        val futures3 = (0..19).map { produceIntoPartition(2) }
        producer.flush()
        listOf(futures1, futures2, futures3).flatten().forEach { it.get(2, TimeUnit.SECONDS) }

        createConsumerAndSubscribe(groupId, topic).use { consumer ->
            //check offsets after commit
            val records = consumer.poolAll()
            assertThat(records).hasSize(10 + 5 + 20)
            consumer.commitSync(Duration.ofSeconds(2))
            val groupAfterCommit = doOnKafka { it.consumerGroup(groupId).get() }
            assertThat(groupAfterCommit.offsets)
                    .extracting<List<Any?>> { listOf(it.topic, it.partition, it.offset) }
                    .containsExactlyInAnyOrder(
                            listOf(topic, 0, 10L),
                            listOf(topic, 1, 5L),
                            listOf(topic, 2, 20L)
                    )
            //check empty state after close
            consumer.close()
            val groupAfterClose = doOnKafka { it.consumerGroup(groupId).get() }
            assertThat(groupAfterClose.status).isEqualTo(ConsumerGroupStatus.EMPTY)
        }

        val resetAllToBegin = GroupOffsetsReset(
                seek = OffsetSeek(OffsetSeekType.EARLIEST, offset = 0),
                topics = listOf(TopicSeek(topic))
        )
        createConsumerAndSubscribe(groupId, topic).use {
            val group = doOnKafka { it.consumerGroup(groupId).get() }
            assertThat(group.offsets)
                    .extracting<List<Any?>> { listOf(it.topic, it.partition, it.offset) }
                    .containsExactlyInAnyOrder(
                            listOf(topic, 0, 10L),
                            listOf(topic, 1, 5L),
                            listOf(topic, 2, 20L)
                    )
            //try to reset with failure because consumer group must not have active members
            assertThatThrownBy { doOnKafka { it.resetConsumerGroup(groupId, resetAllToBegin).get() } }
                    .isInstanceOf(ExecutionException::class.java)
                    .hasCauseInstanceOf(KafkaClusterManagementException::class.java)
        }

        doOnKafka { it.resetConsumerGroup(groupId, resetAllToBegin).get() }
                .also { change ->
                    assertThat(change.totalSkip).isEqualTo(0)
                    assertThat(change.totalRewind).isEqualTo(35)
                    assertThat(change.changes).extracting<List<Any?>> { listOf(it.topic, it.partition, it.offset, it.delta) }
                            .containsExactly(
                                    listOf(topic, 0, 0L, -10L),
                                    listOf(topic, 1, 0L, -5L),
                                    listOf(topic, 2, 0L, -20L)
                            )
                }

        //check offsets after reset
        createConsumerAndSubscribe(groupId, topic).use {
            val group = doOnKafka { it.consumerGroup(groupId).get() }
            assertThat(group.offsets)
                    .extracting<List<Any?>> { listOf(it.topic, it.partition, it.offset) }
                    .containsExactlyInAnyOrder(
                            listOf(topic, 0, 0L),
                            listOf(topic, 1, 0L),
                            listOf(topic, 2, 0L)
                    )
        }

        val resetAllToEnd = GroupOffsetsReset(
                seek = OffsetSeek(OffsetSeekType.LATEST, offset = 0),
                topics = listOf(TopicSeek(topic))
        )
        doOnKafka { it.resetConsumerGroup(groupId, resetAllToEnd).get() }
                .also { change ->
                    assertThat(change.totalSkip).isEqualTo(35)
                    assertThat(change.totalRewind).isEqualTo(0)
                    assertThat(change.changes).extracting<List<Any?>> { listOf(it.topic, it.partition, it.offset, it.delta) }
                            .containsExactly(
                                    listOf(topic, 0, 10L, 10L),
                                    listOf(topic, 1, 5L, 5L),
                                    listOf(topic, 2, 20L, 20L)
                            )
                }

        //check offsets after reset to end
        createConsumerAndSubscribe(groupId, topic).use {
            val group = doOnKafka { it.consumerGroup(groupId).get() }
            assertThat(group.offsets)
                    .extracting<List<Any?>> { listOf(it.topic, it.partition, it.offset) }
                    .containsExactlyInAnyOrder(
                            listOf(topic, 0, 10L),
                            listOf(topic, 1, 5L),
                            listOf(topic, 2, 20L)
                    )
        }

        val resetExplicit = GroupOffsetsReset(
                seek = OffsetSeek(OffsetSeekType.EXPLICIT, offset = 0),
                topics = listOf(TopicSeek(
                        topic = topic,
                        partitions = listOf(
                                PartitionSeek(0, OffsetSeek(OffsetSeekType.EXPLICIT, 7)),
                                PartitionSeek(1, OffsetSeek(OffsetSeekType.EXPLICIT, 7)),
                                PartitionSeek(2, OffsetSeek(OffsetSeekType.EXPLICIT, 7))
                        )
                ))
        )
        doOnKafka { it.resetConsumerGroup(groupId, resetExplicit).get() }
                .also { change ->
                    assertThat(change.totalSkip).isEqualTo(0)
                    assertThat(change.totalRewind).isEqualTo(3L + 0 + 13)
                    assertThat(change.changes).extracting<List<Any?>> { listOf(it.topic, it.partition, it.offset, it.delta) }
                            .containsExactly(
                                    listOf(topic, 0, 7L, -3L),
                                    listOf(topic, 1, 5L, 0L),
                                    listOf(topic, 2, 7L, -13L)
                            )
                }

        //check offsets after explicit offset reset
        createConsumerAndSubscribe(groupId, topic).use {
            val group = doOnKafka { it.consumerGroup(groupId).get() }
            assertThat(group.offsets)
                    .extracting<List<Any?>> { listOf(it.topic, it.partition, it.offset) }
                    .containsExactlyInAnyOrder(
                            listOf(topic, 0, 7L),
                            listOf(topic, 1, 5L),   //can't be 7 because 5 is max
                            listOf(topic, 2, 7L)
                    )
        }

        val resetRelative = GroupOffsetsReset(
                seek = OffsetSeek(OffsetSeekType.RELATIVE, offset = 3L),
                topics = listOf(TopicSeek(topic))
        )
        doOnKafka { it.resetConsumerGroup(groupId, resetRelative).get() }
                .also { change ->
                    assertThat(change.totalSkip).isEqualTo(3L + 0 + 3)
                    assertThat(change.totalRewind).isEqualTo(0)
                    assertThat(change.changes).extracting<List<Any?>> { listOf(it.topic, it.partition, it.offset, it.delta) }
                            .containsExactly(
                                    listOf(topic, 0, 10L, 3L),
                                    listOf(topic, 1, 5L, 0L),
                                    listOf(topic, 2, 10L, 3L)
                            )
                }

        //check offsets after relative offset reset
        createConsumerAndSubscribe(groupId, topic).use {
            val group = doOnKafka { it.consumerGroup(groupId).get() }
            assertThat(group.offsets)
                    .extracting<List<Any?>> { listOf(it.topic, it.partition, it.offset) }
                    .containsExactlyInAnyOrder(
                            listOf(topic, 0, 10L),
                            listOf(topic, 1, 5L),   //5 is already max, can' seek to future
                            listOf(topic, 2, 10L)
                    )
        }

        val timestampBeforeProduce2 = System.currentTimeMillis()

        val futures4 = (0..6).map { produceIntoPartition(0) }
        val futures5 = (0..14).map { produceIntoPartition(1) }
        val futures6 = (0..1).map { produceIntoPartition(2) }
        producer.flush()
        listOf(futures4, futures5, futures6).flatten().forEach { it.get(2, TimeUnit.SECONDS) }

        doOnKafka { it.resetConsumerGroup(groupId, resetAllToEnd).get() }
                .also { change ->
                    assertThat(change.totalSkip).isEqualTo(7L + 15 + 12)
                    assertThat(change.totalRewind).isEqualTo(0)
                    assertThat(change.changes).extracting<List<Any?>> { listOf(it.topic, it.partition, it.offset, it.delta) }
                            .containsExactly(
                                    listOf(topic, 0, 17L, 7L),
                                    listOf(topic, 1, 20L, 15L),
                                    listOf(topic, 2, 22L, 12L)
                            )
                }

        val resetToTimestamp = GroupOffsetsReset(
                seek = OffsetSeek(OffsetSeekType.TIMESTAMP, timestamp = timestampBeforeProduce2),
                topics = listOf(TopicSeek(topic))
        )

        doOnKafka { it.resetConsumerGroup(groupId, resetToTimestamp).get() }
                .also { change ->
                    assertThat(change.totalSkip).isEqualTo(0)
                    assertThat(change.totalRewind).isEqualTo(7L + 15L + 2L)
                    assertThat(change.changes).extracting<List<Any?>> { listOf(it.topic, it.partition, it.offset, it.delta) }
                            .containsExactly(
                                    listOf(topic, 0, 10L, -7L),
                                    listOf(topic, 1, 5L, -15L),
                                    listOf(topic, 2, 20L, -2L)
                            )
                }

        //check offsets after timestamp offset reset
        createConsumerAndSubscribe(groupId, topic).use {
            val group = doOnKafka { it.consumerGroup(groupId).get() }
            assertThat(group.offsets)
                    .extracting<List<Any?>> { listOf(it.topic, it.partition, it.offset) }
                    .containsExactlyInAnyOrder(
                            listOf(topic, 0, 10L),
                            listOf(topic, 1, 5L),
                            listOf(topic, 2, 20L)
                    )
        }

        val resetToFutureTimestamp = GroupOffsetsReset(
                seek = OffsetSeek(OffsetSeekType.TIMESTAMP, timestamp = System.currentTimeMillis()),
                topics = listOf(TopicSeek(topic))
        )

        doOnKafka { it.resetConsumerGroup(groupId, resetToFutureTimestamp).get() }

        //check offsets after future timestamp offset reset sets offset to end
        createConsumerAndSubscribe(groupId, topic).use {
            val group = doOnKafka { it.consumerGroup(groupId).get() }
            assertThat(group.offsets)
                    .extracting<List<Any?>> { listOf(it.topic, it.partition, it.offset) }
                    .containsExactlyInAnyOrder(
                            listOf(topic, 0, 17L),
                            listOf(topic, 1, 20L),
                            listOf(topic, 2, 22L)
                    )
        }

        val cloneConsumerGroup = GroupOffsetsReset(
            seek = OffsetSeek(OffsetSeekType.CLONE, cloneFromConsumerGroup = groupId),
            topics = listOf(TopicSeek(topic))
        )

        val cloneGroupId = "clone-of-$groupId"
        doOnKafka { it.resetConsumerGroup(cloneGroupId, cloneConsumerGroup).get() }
            .also { change ->
                assertThat(change.totalSkip).isEqualTo(0)
                assertThat(change.totalRewind).isEqualTo(0)
                assertThat(change.changes).extracting<List<Any?>> { listOf(it.topic, it.partition, it.offset, it.delta) }
                    .containsExactly(
                        listOf(topic, 0, 17L, null),
                        listOf(topic, 1, 20L, null),
                        listOf(topic, 2, 22L, null)
                    )
            }

        //check offsets after clone
        createConsumerAndSubscribe(cloneGroupId, topic).use {
            val group = doOnKafka { it.consumerGroup(cloneGroupId).get() }
            assertThat(group.offsets)
                .extracting<List<Any?>> { listOf(it.topic, it.partition, it.offset) }
                .containsExactlyInAnyOrder(
                    listOf(topic, 0, 17L),
                    listOf(topic, 1, 20L),
                    listOf(topic, 2, 22L)
                )
        }

        val newGroup = GroupOffsetsReset(
            seek = OffsetSeek(OffsetSeekType.LATEST, offset = 0),
            topics = listOf(TopicSeek(topic))
        )

        val newGroupId = "new-groupId"
        doOnKafka { it.resetConsumerGroup(newGroupId, newGroup).get() }
            .also { change ->
                assertThat(change.totalSkip).isEqualTo(0)
                assertThat(change.totalRewind).isEqualTo(0)
                assertThat(change.changes).extracting<List<Any?>> { listOf(it.topic, it.partition, it.offset, it.delta) }
                    .containsExactly(
                        listOf(topic, 0, 17L, null),
                        listOf(topic, 1, 20L, null),
                        listOf(topic, 2, 22L, null)
                    )
            }

        //check offsets after initializing to latest
        createConsumerAndSubscribe(newGroupId, topic).use {
            val group = doOnKafka { it.consumerGroup(newGroupId).get() }
            assertThat(group.offsets)
                .extracting<List<Any?>> { listOf(it.topic, it.partition, it.offset) }
                .containsExactlyInAnyOrder(
                    listOf(topic, 0, 17L),
                    listOf(topic, 1, 20L),
                    listOf(topic, 2, 22L)
                )
        }
    }

    @Test
    fun `test set-unset broker config`() {
        val clusterInfo1 = doOnKafka { it.clusterInfo("test").get() }
        val leaderThrottleRates1 = clusterInfo1.perBrokerConfig.mapValues {
            it.value["leader.replication.throttled.rate"]?.value
        }
        assertThat(leaderThrottleRates1).isEqualTo(mapOf(
                0 to null, 1 to null, 2 to null
        ))

        doOnKafka { it.setBrokerConfig(0, mapOf("leader.replication.throttled.rate" to "123456")).get() }

        Awaitility.await("for leader.replication.throttled.rate to be set")
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted {
                    val clusterInfo = doOnKafka { it.clusterInfo("test").get() }
                    val leaderThrottleRates = clusterInfo.perBrokerConfig.mapValues {
                        it.value["leader.replication.throttled.rate"]?.value
                    }
                    assertThat(leaderThrottleRates).isEqualTo(mapOf(
                            0 to "123456", 1 to null, 2 to null
                    ))
                }

        doOnKafka { it.unsetBrokerConfig(0, setOf("leader.replication.throttled.rate")).get() }

        Awaitility.await("for leader.replication.throttled.rate to be un-set")
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted {
                    val clusterInfo = doOnKafka { it.clusterInfo("test").get() }
                    val leaderThrottleRates = clusterInfo.perBrokerConfig.mapValues {
                        it.value["leader.replication.throttled.rate"]?.value
                    }
                    assertThat(leaderThrottleRates).isEqualTo(mapOf(
                            0 to null, 1 to null, 2 to null
                    ))
                }
    }

    @Test
    fun `test try to set read-only broker property`() {
        val version = doOnKafka { it.clusterInfo("").get().clusterVersion }
        if (version != null && version < Version.of("2.3")) {
            return
        }
        assertThatThrownBy {
            doOnKafka { it.setBrokerConfig(0, mapOf("auto.leader.rebalance.enable" to "true")).get() }
        }.rootCause().isInstanceOf(InvalidRequestException::class.java)
    }

    @Test
    fun `test reassignment in progress`() {
        val oldAssignment = mapOf(
                0 to listOf(0, 1),
                1 to listOf(1, 2)
        )
        doOnKafka {
            it.createTopic(KafkaTopicConfiguration(
                    name = "re-assignment-progress-test",
                    partitionsReplicas = oldAssignment,
                    config = mapOf("retention.bytes" to "111222333"),
            )).get()
            it.awaitTopicCreated("re-assignment-progress-test")
        }
        val numMessages = 50_000
        val msgSize = 1000
        val futures = (1..numMessages).map {
            producer.send(ProducerRecord(
                    "re-assignment-progress-test", it % 2, "key-$it", ByteArray(msgSize)
            ))
        }
        producer.flush()
        futures.forEach { it.get(10, TimeUnit.SECONDS) }

        val replicaInfos = doOnKafka { it.describeReplicas().get() }
        val expectedReplicaSizeMin = numMessages * msgSize / 2
        val expectedReplicaSizeMax = numMessages * (msgSize + 200) / 2
        replicaInfos.forEach { replicaInfo ->
            assertThat(replicaInfo.sizeBytes).`as`("Size of $replicaInfo")
                    .isBetween(expectedReplicaSizeMin.toLong(), expectedReplicaSizeMax.toLong())
        }

        val newAssignment = mapOf(
                0 to listOf(1, 2),
                1 to listOf(2, 0)
        )

        //there will be 2 replicas moved
        // p0: b0 -> b2
        // p1: b1 -> b0
        // we have independent migrations, i.e.
        // speed needed to complete ~5sec: speed = replica_size / 5s
        val neededSpeed = expectedReplicaSizeMax / 5
        log.info("Needed speed {} B/sec for {}", neededSpeed, replicaInfos)
        doOnKafka {
            it.reAssignPartitions("re-assignment-progress-test", newAssignment, neededSpeed).get()
        }

        val reAssignmentStarted = System.currentTimeMillis()

        Thread.sleep(1_000)
        val reAssignments = doOnKafka { it.listReAssignments().get() }
        assertThat(reAssignments).`as`("ReAssignments in progress").containsExactlyInAnyOrder(
                TopicPartitionReAssignment("re-assignment-progress-test", 0,
                        addingReplicas = listOf(2), removingReplicas = listOf(0), allReplicas = listOf(1, 2, 0)
                ),
                TopicPartitionReAssignment("re-assignment-progress-test", 1,
                        addingReplicas = listOf(0), removingReplicas = listOf(1), allReplicas = listOf(2, 0, 1)
                ),
        )

        verifyReAssignment("re-assignment-progress-test", newAssignment)

        val reAssignmentCompleted = System.currentTimeMillis()
        assertThat(reAssignmentCompleted - reAssignmentStarted)
            .`as`("ReAssignment duration (nominal 5sec)")
            .isBetween(4_000, 8_000)

        assertThat(doOnKafka { it.listReAssignments().get() }).`as`("ReAssignments after completion").isEmpty()

        val topicAfter = doOnKafka { it.listAllTopics().get() }.first { it.name == "re-assignment-progress-test" }
        assertThat(topicAfter.config["retention.bytes"])
            .`as`("topic config should not be influenced by re-assignment")
            .isEqualTo(
                ConfigValue(
                    value = "111222333",
                    default = false, readOnly = false, sensitive = false,
                    source = ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG
                )
            )
    }

    @Test
    fun `test cancel reassignment in progress`() {
        val oldAssignment = mapOf(
                0 to listOf(0, 1),
                1 to listOf(1, 2)
        )
        doOnKafka {
            it.createTopic(KafkaTopicConfiguration(
                    name = "cancel-re-assignment-progress-test",
                    partitionsReplicas = oldAssignment,
                    config = emptyMap()
            )).get()
            it.awaitTopicCreated("cancel-re-assignment-progress-test")
        }

        val kafkaVersion = doOnKafka { it.clusterInfo("").get().clusterVersion ?: Version.of("0")}
        if (kafkaVersion < Version.of("2.4")) {
            assertThatThrownBy {
                doOnKafka { it.cancelReAssignments("cancel-re-assignment-progress-test", listOf(0, 1)) }
            }.isInstanceOf(KafkaClusterManagementException::class.java)
                    .hasMessageContaining("Unsupported", "version")
            return
        }

        val numMessages = 50_000
        val msgSize = 1000
        val futures = (1..numMessages).map {
            producer.send(ProducerRecord(
                    "cancel-re-assignment-progress-test", it % 2, "key-$it", ByteArray(msgSize)
            ))
        }
        producer.flush()
        futures.forEach { it.get(10, TimeUnit.SECONDS) }

        val newAssignment = mapOf(
                0 to listOf(1, 2),
                1 to listOf(2, 0)
        )

        doOnKafka {
            it.reAssignPartitions("cancel-re-assignment-progress-test", newAssignment, 2000)
        }

        val seenReAssignments = mutableSetOf<TopicPartitionReAssignment>()
        Awaitility.await("for assignments in progress to show on listReAssignments()")
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted {
                val reAssignments = doOnKafka { it.listReAssignments().get() }
                seenReAssignments.addAll(reAssignments)
                assertThat(seenReAssignments).`as`("ReAssignments in progress").hasSize(2)
            }

        doOnKafka { it.cancelReAssignments("cancel-re-assignment-progress-test", listOf(0, 1)).get() }
        Awaitility.await("cancelation to be applied")
            .atMost(Duration.ofSeconds(5))
            .untilAsserted {
                val newAssignmentsVerification = doOnKafka { it.verifyReAssignPartitions("cancel-re-assignment-progress-test", newAssignment) }
                assertThat(newAssignmentsVerification)
                    .contains("failed")
                    .doesNotContain("Topic: Throttle was removed")
            }

        verifyReAssignment("cancel-re-assignment-progress-test", oldAssignment)

        assertThat(doOnKafka { it.listReAssignments().get() }).`as`("ReAssignments after canceling").isEmpty()
    }

    @Test
    fun `test quotas crud`() {
        //check empty initially
        val quotasInitial = doOnKafka { it.listQuotas().get() }
        assertThat(quotasInitial).isEmpty()

        //create quota and check created
        val userFooQuota = ClientQuota(
            entity = QuotaEntity.user("foo"),
            properties = QuotaProperties(producerByteRate = 2048, requestPercentage = 125.0)
        )
        doOnKafka { it.setClientQuotas(listOf(userFooQuota)).get() }
        Awaitility.await("for $userFooQuota to be created")
            .atMost(Duration.ofSeconds(1))
            .untilAsserted {
                val quotas = doOnKafka { it.listQuotas().get() }
                assertThat(quotas).containsExactlyInAnyOrder(userFooQuota)
            }

        //update quota and check created
        val userFooQuotaUpdated = ClientQuota(
            entity = QuotaEntity.user("foo"),
            properties = QuotaProperties(consumerByteRate = 1024, requestPercentage = 250.0)
        )
        doOnKafka { it.setClientQuotas(listOf(userFooQuotaUpdated)).get() }
        Awaitility.await("for $userFooQuota to be updated")
            .atMost(Duration.ofSeconds(1))
            .untilAsserted {
                val quotas = doOnKafka { it.listQuotas().get() }
                assertThat(quotas).containsExactlyInAnyOrder(userFooQuotaUpdated)
            }

        //delete created and check empty
        doOnKafka { it.removeClientQuotas(listOf(userFooQuota.entity)).get() }
        Awaitility.await("for $userFooQuota to be deleted")
            .atMost(Duration.ofSeconds(1))
            .untilAsserted {
                val quotas = doOnKafka { it.listQuotas().get() }
                assertThat(quotas).isEmpty()
            }

        //create each type of quotas entities
        fun quotaProps(value: Int) = QuotaProperties(value.toLong(), value.toLong(), value.toDouble())
        val manyQuotas = listOf(
            ClientQuota(QuotaEntity.userClient("u-1", "c-1"), quotaProps(1)),
            ClientQuota(QuotaEntity.userClientDefault("c-2"), quotaProps(2)),
            ClientQuota(QuotaEntity.user("u-3"), quotaProps(3)),
            ClientQuota(QuotaEntity.userDefaultClient("c-4"), quotaProps(4)),
            ClientQuota(QuotaEntity.userDefaultClientDefault(), quotaProps(5)),
            ClientQuota(QuotaEntity.userDefault(), quotaProps(6)),
            ClientQuota(QuotaEntity.client("c-7"), quotaProps(7)),
            ClientQuota(QuotaEntity.clientDefault(), quotaProps(8)),
        )
        doOnKafka { it.setClientQuotas(manyQuotas).get() }
        Awaitility.await("for all types of entities to be created")
            .atMost(Duration.ofSeconds(100))
            .untilAsserted {
                val quotas = doOnKafka { it.listQuotas().get() }
                assertThat(quotas).containsExactlyInAnyOrderElementsOf(manyQuotas)
            }

        //remove each type of quotas
        doOnKafka { it.removeClientQuotas(manyQuotas.map { q -> q.entity }).get() }
        Awaitility.await("for all types of entities to be deleted")
            .atMost(Duration.ofSeconds(1))
            .untilAsserted {
                val quotas = doOnKafka { it.listQuotas().get() }
                assertThat(quotas).isEmpty()
            }
    }

    @Test
    fun `test topic default configs from cluster properties`() {
        val clusterConfig = doOnKafka { it.clusterInfo("test").get() }.config

        fun SoftAssertions.assertDefaultConfig(topicConfigKey: String) = assertThat(
            ClusterTopicDefaultConfigs
                .extractClusterExpectedValue(clusterConfig, topicConfigKey)
                ?.value
        ).`as`("Default config for '$topicConfigKey'")

        fun StringAssert.isEqualToStringOf(expected: Any) = apply {
            isEqualTo(expected.toString())
        }

        println("Cluster config is (size: ${clusterConfig.size}):")
        clusterConfig.entries.forEach { (key, value)  ->
            println("\t$key -> $value")
        }

        SoftAssertions().apply {
            assertDefaultConfig(TopicConfig.CLEANUP_POLICY_CONFIG).isEqualTo("delete")
            assertDefaultConfig(TopicConfig.COMPRESSION_TYPE_CONFIG).isEqualTo("producer")
            assertDefaultConfig(TopicConfig.DELETE_RETENTION_MS_CONFIG).isEqualToStringOf(24 * 3600 * 1000L)
            if (this@ClusterOperationsTestSuite is ClusterOpsKafkaZkEmbeddedTest) {
                assertDefaultConfig(TopicConfig.FILE_DELETE_DELAY_MS_CONFIG).isEqualToStringOf(1000L)
            } else {
                assertDefaultConfig(TopicConfig.FILE_DELETE_DELAY_MS_CONFIG).isEqualToStringOf(60 * 1000L)
            }
            assertDefaultConfig(TopicConfig.FLUSH_MESSAGES_INTERVAL_CONFIG).isEqualToStringOf(Long.MAX_VALUE)
            assertDefaultConfig(TopicConfig.FLUSH_MS_CONFIG).isEqualToStringOf(Long.MAX_VALUE)
            assertDefaultConfig(TopicConfig.INDEX_INTERVAL_BYTES_CONFIG).isEqualToStringOf(4096)
            if (expectedClusterVersion > Version.of("3.4")) {
                assertDefaultConfig(TopicConfig.LOCAL_LOG_RETENTION_BYTES_CONFIG).isEqualToStringOf(-2)
                assertDefaultConfig(TopicConfig.LOCAL_LOG_RETENTION_MS_CONFIG).isEqualToStringOf(-2)
                assertDefaultConfig(TopicConfig.REMOTE_LOG_STORAGE_ENABLE_CONFIG).isEqualToStringOf(false)
            } else {
                assertDefaultConfig(TopicConfig.LOCAL_LOG_RETENTION_BYTES_CONFIG).isNull()
                assertDefaultConfig(TopicConfig.LOCAL_LOG_RETENTION_MS_CONFIG).isNull()
                assertDefaultConfig(TopicConfig.REMOTE_LOG_STORAGE_ENABLE_CONFIG).isNull()
            }
            if (expectedClusterVersion > Version.of("2.1")) {
                assertDefaultConfig(TopicConfig.MAX_COMPACTION_LAG_MS_CONFIG).isEqualToStringOf(Long.MAX_VALUE)
            } else {
                assertDefaultConfig(TopicConfig.MAX_COMPACTION_LAG_MS_CONFIG).isNull()
            }
            if (expectedClusterVersion > Version.of("2.3")) {
                assertDefaultConfig(TopicConfig.MAX_MESSAGE_BYTES_CONFIG).isEqualToStringOf(1024 * 1024 + 12)
            } else {
                assertDefaultConfig(TopicConfig.MAX_MESSAGE_BYTES_CONFIG).isEqualToStringOf(1_000_000 + 12)
            }
            assertDefaultConfig(TopicConfig.MESSAGE_DOWNCONVERSION_ENABLE_CONFIG).isEqualTo("true")
            assertDefaultConfig("message.format.version").startsWith("" + expectedClusterVersion.major() + ".")
            @Suppress("DEPRECATION")
            assertDefaultConfig(TopicConfig.MESSAGE_TIMESTAMP_DIFFERENCE_MAX_MS_CONFIG).isEqualToStringOf(Long.MAX_VALUE)
            assertDefaultConfig(TopicConfig.MESSAGE_TIMESTAMP_TYPE_CONFIG).isEqualTo("CreateTime")
            assertDefaultConfig(TopicConfig.MIN_CLEANABLE_DIRTY_RATIO_CONFIG).isEqualToStringOf(0.5)
            assertDefaultConfig(TopicConfig.MIN_COMPACTION_LAG_MS_CONFIG).isEqualToStringOf(0)
            assertDefaultConfig(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG).isEqualToStringOf(1)
            assertDefaultConfig(TopicConfig.PREALLOCATE_CONFIG).isEqualTo("false")
            assertDefaultConfig(TopicConfig.RETENTION_BYTES_CONFIG).isEqualToStringOf(-1)
            assertDefaultConfig(TopicConfig.RETENTION_MS_CONFIG).isEqualToStringOf(7 * 24 * 3600 * 1000L)
            assertDefaultConfig(TopicConfig.SEGMENT_BYTES_CONFIG).isEqualToStringOf(1024L * 1024 * 1024)
            assertDefaultConfig(TopicConfig.SEGMENT_INDEX_BYTES_CONFIG).isEqualToStringOf(10L * 1024 * 1024)
            assertDefaultConfig(TopicConfig.SEGMENT_JITTER_MS_CONFIG).isEqualToStringOf(0)
            assertDefaultConfig(TopicConfig.SEGMENT_MS_CONFIG).isEqualToStringOf(7 * 24 * 3600 * 1000L)
            assertDefaultConfig(TopicConfig.UNCLEAN_LEADER_ELECTION_ENABLE_CONFIG).isEqualTo("false")
        }.assertAll()
    }

    private fun verifyReAssignment(topicName: TopicName, assignments: Map<Partition, List<BrokerId>>) {
        doOnKafka {
            Awaitility.await("re-assignment of $topicName to complete and verify succeeds")
                    .timeout(20L, TimeUnit.SECONDS)
                    .pollInterval(1L, TimeUnit.SECONDS)
                    .until {
                        val result = it.verifyReAssignPartitions(topicName, assignments)
                        log.info("Verify attempt output:\n{}", result)
                        "Throttle was removed" in result
                    }
        }
    }

    private fun Map<String, ConfigValue>.filterOutFalseDefaults() = filterKeys {
        it !in setOf("segment.bytes", "file.delete.delay.ms", "message.timestamp.difference.max.ms")
    }

    private fun createAssignments(partitionCount: Int, replicationFactor: Int): Map<Partition, List<BrokerId>> {
        val brokersDoubleList = clusterBrokerIds + clusterBrokerIds
        return (0 until partitionCount).associateWith { partition ->
            val index = partition % (clusterBrokerIds.size)
            brokersDoubleList.subList(index, index + replicationFactor)
        }
    }

    private fun Map<Partition, List<BrokerId>>.reduceReplicationFactor(
        targetReplicationFactor: Int,
    ): Map<Partition, List<BrokerId>> {
        return mapValues { (_, replicas) ->
            replicas.subList(0, targetReplicationFactor)
        }
    }

    private val producer: KafkaProducer<String, ByteArray> by lazy {
        KafkaProducer(
                Properties().also { props ->
                    props["bootstrap.servers"] = clusterConnection
                    props["acks"] = "all"
                    props["retries"] = 0
                    props["batch.size"] = 16384
                    props["linger.ms"] = 1
                    props["buffer.memory"] = 33554432
                },
                StringSerializer(),
                ByteArraySerializer()
        )
    }

    @AfterEach
    fun tearDown() = producer.close()

    @Suppress("SameParameterValue")
    private fun createConsumerAndSubscribe(
        group: ConsumerGroupId = "consumer",
        topic: TopicName = "topic"
    ): KafkaConsumer<String, ByteArray> {
        val props = Properties().also { props ->
            props[ConsumerConfig.GROUP_ID_CONFIG] = group
            props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = clusterConnection
            props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false"
            props[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "10"
            props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
            props[ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG] = "2000"
            props[ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG] = "2000"
            props[ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG] = "20000"
            props[ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG] = "30000"
        }
        val consumer = KafkaConsumer(props, StringDeserializer(), ByteArrayDeserializer())
        consumer.subscribe(listOf(topic))
        val records = consumer.poll(Duration.ofSeconds(2))
        records.groupingBy { TopicPartition(it.topic(), it.partition()) }
                .fold(0L) { minOffset, record -> minOffset.coerceAtMost(record.offset()) }
                .forEach { (topicPartition, minOffset) -> consumer.seek(topicPartition, minOffset) }
        return consumer
    }

    private fun KafkaManagementClient.awaitTopicCreated(topic: TopicName) {
        Awaitility.await("For topic '$topic' to be created")
            .atMost(5, TimeUnit.SECONDS)
            .untilAsserted {
                repeat(3) {
                    assertThat(listAllTopicNames().get()).contains(topic)
                }
            }
    }

}
