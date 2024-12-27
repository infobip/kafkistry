package com.infobip.kafkistry.service.generator.balance

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.infobip.kafkistry.service.asBrokers
import com.infobip.kafkistry.service.generator.Broker
import com.infobip.kafkistry.service.generator.BrokerLoad
import com.infobip.kafkistry.service.generator.PartitionsReplicasAssignor
import com.infobip.kafkistry.service.generator.ids
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.random.Random

fun state1(): GlobalState {
    val loads = listOf(
            TopicLoad(
                    topic = "t1",
                    partitionLoads = listOf(
                            PartitionLoad(101, 10.0, 1),
                            PartitionLoad(102, 10.0, 1),
                            PartitionLoad(103, 10.0, 1),
                            PartitionLoad(100, 10.0, 1),
                            PartitionLoad(50, 10.0, 1),
                            PartitionLoad(100, 10.0, 1)
                    )
            ),
            TopicLoad(
                    topic = "t2",
                    partitionLoads = listOf(
                            PartitionLoad(250, 20.0, 1),
                            PartitionLoad(200, 20.0, 1),
                            PartitionLoad(200, 20.0, 1)
                    )
            )
    )
    val assignments = listOf(
            TopicAssignments(
                    topic = "t1",
                    partitionAssignments = mapOf(
                            0 to listOf(0),
                            1 to listOf(1),
                            2 to listOf(2),
                            3 to listOf(0),
                            4 to listOf(1),
                            5 to listOf(2)
                    )
            ),
            TopicAssignments(
                    topic = "t2",
                    partitionAssignments = mapOf(
                            0 to listOf(0),
                            1 to listOf(1),
                            2 to listOf(2)
                    )
            )
    )
    val brokerIds = listOf(0, 1, 2).asBrokers()
    return GlobalState(brokerIds, loads.associateBy { it.topic }, assignments.associateBy { it.topic })
}

fun state2(): GlobalState {
    val loads = listOf(
            TopicLoad(
                    topic = "t1",
                    partitionLoads = listOf(
                            PartitionLoad(100, 10.0, 1),
                            PartitionLoad(100, 10.0, 1),
                            PartitionLoad(200, 15.0, 1),
                            PartitionLoad(200, 30.0, 1),
                            PartitionLoad(50,  10.0, 1),
                            PartitionLoad(50,  20.0, 1)
                    )
            ),
            TopicLoad(
                    topic = "t2",
                    partitionLoads = listOf(
                            PartitionLoad(50,  20.0, 2),
                            PartitionLoad(290, 20.0, 2),
                            PartitionLoad(100, 10.0, 2)
                    )
            ),
            TopicLoad(
                    topic = "t3",
                    partitionLoads = listOf(
                            PartitionLoad(50,  20.0, 4),
                            PartitionLoad(50,  10.0, 4),
                            PartitionLoad(100, 25.0, 4),
                            PartitionLoad(200, 40.0, 4),
                            PartitionLoad(150, 30.0, 4),
                            PartitionLoad(50,  20.0, 4)
                    )
            )
    )
    val assignments = listOf(
            TopicAssignments(
                    topic = "t1",
                    partitionAssignments = mapOf(
                            0 to listOf(2, 0),
                            1 to listOf(0, 1),
                            2 to listOf(1, 2),
                            3 to listOf(1, 2),
                            4 to listOf(0, 1),
                            5 to listOf(2, 0)
                    )
            ),
            TopicAssignments(
                    topic = "t2",
                    partitionAssignments = mapOf(
                            0 to listOf(0),
                            1 to listOf(1),
                            2 to listOf(2)
                    )
            ),
            TopicAssignments(
                    topic = "t3",
                    partitionAssignments = mapOf(
                            0 to listOf(0, 1),
                            1 to listOf(1, 2),
                            2 to listOf(2, 0),
                            3 to listOf(0, 1),
                            4 to listOf(1, 2),
                            5 to listOf(2, 0)
                    )
            )
    )
    val brokerIds = listOf(0, 1, 2).asBrokers()
    return GlobalState(brokerIds, loads.associateBy { it.topic }, assignments.associateBy { it.topic })
}

fun state3(): GlobalState {
    val loads = listOf(
        TopicLoad(
            topic = "t1",
            partitionLoads = listOf(
                PartitionLoad(10_000, 0.0, 0),
                PartitionLoad(100, 0.0, 0),
            )
        ),
        TopicLoad(
            topic = "t2",
            partitionLoads = listOf(
                PartitionLoad(100, 0.0, 0),
                PartitionLoad(100, 0.0, 0),
            )
        ),
        TopicLoad(
            topic = "t3",
            partitionLoads = listOf(
                PartitionLoad(100, 0.0, 0)
            )
        )
    )
    val assignments = listOf(
        TopicAssignments(
            topic = "t1",
            partitionAssignments = mapOf(
                0 to listOf(0),
                1 to listOf(2),
            )
        ),
        TopicAssignments(
            topic = "t2",
            partitionAssignments = mapOf(
                0 to listOf(0),
                1 to listOf(2)
            )
        ),
        TopicAssignments(
            topic = "t3",
            partitionAssignments = mapOf(
                0 to listOf(1)
            )
        )
    )
    /*
        0       1       2
        10_100  100     200
        t1[0]   t3[0]   t1[1]
        t2[0]           t2[1]
    */
    val brokerIds = listOf(0, 1, 2).asBrokers()
    return GlobalState(brokerIds, loads.associateBy { it.topic }, assignments.associateBy { it.topic })
}

fun state4(): GlobalState {
    val json = """
        {
          "brokers" : [ 
            {"id": 0},
            {"id": 1},
            {"id": 2},
            {"id": 3},
            {"id": 4},
            {"id": 5}
          ],
          "loads" : {
            "__consumer_offsets" : {
              "topic" : "__consumer_offsets",
              "partitionLoads" : [ {
                "size" : 0,
                "rate" : 0.0,
                "consumers" : 0
              }, {
                "size" : 0,
                "rate" : 0.0,
                "consumers" : 0
              }, {
                "size" : 0,
                "rate" : 0.0,
                "consumers" : 0
              }, {
                "size" : 1511,
                "rate" : 0.0,
                "consumers" : 0
              }, {
                "size" : 0,
                "rate" : 0.0,
                "consumers" : 0
              } ]
            },
            "topic-example-1" : {
              "topic" : "topic-example-1",
              "partitionLoads" : [ {
                "size" : 0,
                "rate" : 0.0,
                "consumers" : 0
              } ]
            },
            "topic-example-2" : {
              "topic" : "topic-example-2",
              "partitionLoads" : [ {
                "size" : 0,
                "rate" : 0.0,
                "consumers" : 0
              }, {
                "size" : 0,
                "rate" : 0.0,
                "consumers" : 0
              }, {
                "size" : 0,
                "rate" : 0.0,
                "consumers" : 0
              }, {
                "size" : 0,
                "rate" : 0.0,
                "consumers" : 0
              }, {
                "size" : 0,
                "rate" : 0.0,
                "consumers" : 0
              }, {
                "size" : 0,
                "rate" : 0.0,
                "consumers" : 0
              } ]
            },
            "topic-example-3" : {
              "topic" : "topic-example-3",
              "partitionLoads" : [ {
                "size" : 0,
                "rate" : 0.0,
                "consumers" : 0
              }, {
                "size" : 0,
                "rate" : 0.0,
                "consumers" : 0
              }, {
                "size" : 0,
                "rate" : 0.0,
                "consumers" : 0
              }, {
                "size" : 0,
                "rate" : 0.0,
                "consumers" : 0
              } ]
            },
            "topic-example-4" : {
              "topic" : "topic-example-4",
              "partitionLoads" : [ {
                "size" : 6672566,
                "rate" : 296.7909162182198,
                "consumers" : 0
              }, {
                "size" : 5996629,
                "rate" : 266.2894202102411,
                "consumers" : 0
              }, {
                "size" : 5375736,
                "rate" : 237.6732675907605,
                "consumers" : 0
              }, {
                "size" : 4681399,
                "rate" : 206.9344369836639,
                "consumers" : 0
              }, {
                "size" : 4022712,
                "rate" : 177.5316135019387,
                "consumers" : 0
              }, {
                "size" : 3371024,
                "rate" : 148.12345665843552,
                "consumers" : 0
              } ]
            },
            "topic-example-5-whatever" : {
              "topic" : "topic-example-5-whatever",
              "partitionLoads" : [ {
                "size" : 0,
                "rate" : 0.0,
                "consumers" : 0
              } ]
            },
            "topic-with-records" : {
              "topic" : "topic-with-records",
              "partitionLoads" : [ {
                "size" : 13581797,
                "rate" : 583.1946851865808,
                "consumers" : 0
              }, {
                "size" : 12352298,
                "rate" : 528.4175560438032,
                "consumers" : 0
              }, {
                "size" : 11039474,
                "rate" : 466.57951640299365,
                "consumers" : 0
              }, {
                "size" : 9665106,
                "rate" : 408.4429474953478,
                "consumers" : 0
              }, {
                "size" : 8305148,
                "rate" : 350.0194204024371,
                "consumers" : 0
              }, {
                "size" : 6984800,
                "rate" : 291.91183717007056,
                "consumers" : 0
              } ]
            }
          },
          "assignments" : {
            "__consumer_offsets" : {
              "topic" : "__consumer_offsets",
              "partitionAssignments" : {
                "0" : [ 1 ],
                "1" : [ 2 ],
                "2" : [ 3 ],
                "3" : [ 4 ],
                "4" : [ 5 ]
              }
            },
            "topic-example-1" : {
              "topic" : "topic-example-1",
              "partitionAssignments" : {
                "0" : [ 0, 1, 2 ]
              }
            },
            "topic-example-2" : {
              "topic" : "topic-example-2",
              "partitionAssignments" : {
                "0" : [ 0 ],
                "1" : [ 1 ],
                "2" : [ 2 ],
                "3" : [ 3 ],
                "4" : [ 4 ],
                "5" : [ 5 ]
              }
            },
            "topic-example-3" : {
              "topic" : "topic-example-3",
              "partitionAssignments" : {
                "0" : [ 2, 4, 0 ],
                "1" : [ 1, 3, 5 ],
                "2" : [ 0, 2, 4 ],
                "3" : [ 5, 1, 3 ]
              }
            },
            "topic-example-4" : {
              "topic" : "topic-example-4",
              "partitionAssignments" : {
                "0" : [ 5, 0, 1 ],
                "1" : [ 4, 5, 3 ],
                "2" : [ 1, 3, 0 ],
                "3" : [ 2, 4, 5 ],
                "4" : [ 0, 1, 2 ],
                "5" : [ 3, 2, 4 ]
              }
            },
            "topic-example-5-whatever" : {
              "topic" : "topic-example-5-whatever",
              "partitionAssignments" : {
                "0" : [ 4 ]
              }
            },
            "topic-with-records" : {
              "topic" : "topic-with-records",
              "partitionAssignments" : {
                "0" : [ 0, 2, 3, 5 ],
                "1" : [ 1, 2, 3, 4 ],
                "2" : [ 2, 3, 4, 1 ],
                "3" : [ 3, 4, 5, 0 ],
                "4" : [ 4, 5, 0, 1 ],
                "5" : [ 5, 0, 1, 2 ]
              }
            }
          }
        }
    """.trimIndent()
    return jacksonObjectMapper().readValue(json, GlobalState::class.java)
}

fun state5(): GlobalState {
    val loads = listOf(
        TopicLoad(
            topic = "t1", partitionLoads = (0 until 3).map { PartitionLoad.ZERO }
        ),
        TopicLoad(
            topic = "t2", partitionLoads = (0 until 3).map { PartitionLoad.ZERO }
        )
    )
    val assignments = listOf(
        TopicAssignments(
            topic = "t1",
            partitionAssignments = mapOf(
                0 to listOf(0, 1),
                1 to listOf(2, 3),
                2 to listOf(4, 5)
            )
        ),
        TopicAssignments(
            topic = "t2",
            partitionAssignments = mapOf(
                0 to listOf(1, 0),
                1 to listOf(3, 2),
                2 to listOf(4, 5)
            )
        )
    )
    val brokerIds = listOf(0, 1, 2, 3, 4, 5).asBrokers()
    return GlobalState(brokerIds, loads.associateBy { it.topic }, assignments.associateBy { it.topic })
}

fun stateEachTopicUnevenImpactPerBroker(): GlobalState {
    val allBrokers: List<Broker> = (1..4).toList().asBrokers()
    val assignor = PartitionsReplicasAssignor()
    val assignments = (1..2).map {
        val initialAssignment = assignor.assignNewPartitionReplicas(
            existingAssignments = emptyMap(),
            allBrokers = allBrokers,
            numberOfNewPartitions = 3,
            replicationFactor = 2,
            existingPartitionLoads = emptyMap()
        ).newAssignments
        TopicAssignments(topic = "topic-$it", partitionAssignments = initialAssignment)
    }
    val loads = assignments.map {
        TopicLoad(topic = it.topic, partitionLoads = it.partitionAssignments.keys.map { PartitionLoad.ZERO })
    }
    return GlobalState(allBrokers, loads.associateBy { it.topic }, assignments.associateBy { it.topic })
}

fun stateRandom(
    randomSeed: Long = 0L,
    brokers: Int = 6,
    topics: Int = 100,
    partitions: List<Int> = listOf(1, 2, 3, 6, 12, 24, 48),
    replication: List<Int> = listOf(2, 3, 4),
    minLoad: PartitionLoad = PartitionLoad(100, 10.0, 0),
    maxLoad: PartitionLoad = PartitionLoad(5_000, 500.0, 10),
    maxLoadDeviation: Double = 1.8
): GlobalState {
    val random = Random(randomSeed)
    val assignor = PartitionsReplicasAssignor()
    val allBrokers: List<Broker> = (1..brokers).toList().asBrokers()
    val brokerIds = allBrokers.ids()

    fun Random.nextLoad() = PartitionLoad(
        size = nextLong(minLoad.size, maxLoad.size),
        rate = nextDouble(minLoad.rate, maxLoad.rate),
        consumers = nextInt(minLoad.consumers, maxLoad.consumers)
    )

    fun Random.deviateLoad(load: PartitionLoad) = if (maxLoadDeviation > 1) PartitionLoad(
        size = load.size.times(nextDouble(1 / maxLoadDeviation, maxLoadDeviation)).roundToLong(),
        rate = load.rate.times(nextDouble(1 / maxLoadDeviation, maxLoadDeviation)),
        consumers = load.consumers.times(nextDouble(1 / maxLoadDeviation, maxLoadDeviation)).roundToInt(),
    ) else load

    fun Random.partitions(): Int = partitions[nextInt(partitions.size)]
    fun Random.replication(): Int = replication[nextInt(replication.size)]
    val brokerPartitions = brokerIds.associateWith {
        BrokerLoad(0, 0, 0L)
    }.toMutableMap()
    fun Random.createAssignments(topic: Int): TopicAssignments {
        val numberOfNewPartitions = partitions()
        return TopicAssignments(
            topic = "t_$topic",
            partitionAssignments = assignor.assignNewPartitionReplicas(
                existingAssignments = emptyMap(),
                numberOfNewPartitions = numberOfNewPartitions,
                replicationFactor = replication(),
                allBrokers = allBrokers,
                existingPartitionLoads = (0 until numberOfNewPartitions).associateWith { com.infobip.kafkistry.service.generator.PartitionLoad(0) },
                clusterBrokersLoad = brokerPartitions
            ).newAssignments
        ).also { assignments ->
            assignments.partitionAssignments.forEach { (_, brokers) ->
                brokers.forEach { brokerPartitions.merge(it, BrokerLoad(1, 0, 0L), BrokerLoad::plus) }
            }
        }
    }

    val assignments = (1..topics).map { random.createAssignments(it) }
    val loads = assignments.map { topicAssignments ->
        val load = random.nextLoad()
        TopicLoad(
            topic = topicAssignments.topic,
            partitionLoads = topicAssignments.partitionAssignments.map { random.deviateLoad(load) }
        )
    }
    return GlobalState(allBrokers, loads.associateBy { it.topic }, assignments.associateBy { it.topic })
}

private operator fun BrokerLoad.plus(other: BrokerLoad) = BrokerLoad(
    numReplicas + other.numReplicas,
    numPreferredLeaders + other.numPreferredLeaders,
    diskBytes + other.diskBytes,
)
