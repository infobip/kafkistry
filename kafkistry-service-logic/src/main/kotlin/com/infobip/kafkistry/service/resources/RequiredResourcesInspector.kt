package com.infobip.kafkistry.service.resources

import com.google.common.math.IntMath
import com.infobip.kafkistry.kafka.ClusterInfo
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.model.ClusterRef
import org.springframework.stereotype.Component
import java.lang.Math.multiplyExact
import java.math.RoundingMode

@Component
class RequiredResourcesInspector {

    fun inspectTopicResources(
        topicProperties: TopicProperties,
        resourceRequirements: ResourceRequirements,
        clusterRef: ClusterRef,
        clusterInfo: ClusterInfo?
    ): TopicResourceRequiredUsages {
        val messagesRate = resourceRequirements.messagesRate(clusterRef)
        val retention = resourceRequirements.retention(clusterRef)
        val totalDiskUsageBytes = messagesRate.ratePerMs()
                .times(retention.toMillis())
                .times(resourceRequirements.avgMessageSize.toBytes())
                .times(topicProperties.replicationFactor)
                .toLongCheckOverflow("totalDiskUsageBytes")
        val numPartitionReplicas = with(topicProperties) { partitionCount * replicationFactor }
        val diskUsagePerPartitionReplica = totalDiskUsageBytes / numPartitionReplicas
        val totalInputBytesPerSec = messagesRate.ratePerSec() * resourceRequirements.avgMessageSize.toBytes()
        val partitionInBytesPerSec = totalInputBytesPerSec / topicProperties.partitionCount
        val numPartitionFollowers = topicProperties.replicationFactor - 1
        val partitionSyncOutBytesPerSec = partitionInBytesPerSec * numPartitionFollowers
        val brokerUsage = clusterInfo?.nodeIds?.size?.let { numBrokers ->
            val maxPartitionReplicasOnBroker = IntMath.divide(numPartitionReplicas, numBrokers, RoundingMode.CEILING)
            val maxLeadersOnBroker = IntMath.divide(topicProperties.partitionCount, numBrokers, RoundingMode.CEILING)
            val maxFollowersOnBroker = IntMath.divide(topicProperties.partitionCount * numPartitionFollowers, numBrokers, RoundingMode.CEILING)
            BrokerUsage(
                    numBrokers = numBrokers,
                    diskUsagePerBroker = checkOverflow("diskUsagePerBroker") {
                        multiplyExact(maxPartitionReplicasOnBroker.toLong(), totalDiskUsageBytes) / numPartitionReplicas
                    },
                    brokerProducerInBytesPerSec = maxLeadersOnBroker * partitionInBytesPerSec,
                    brokerSyncBytesPerSec = maxFollowersOnBroker * partitionInBytesPerSec
            )
        }
        return TopicResourceRequiredUsages(
                numBrokers = brokerUsage?.numBrokers,
                messagesPerSec = messagesRate.ratePerSec(),
                bytesPerSec = totalInputBytesPerSec,
                producedBytesPerDay = messagesRate.ratePerDay()
                        .times(resourceRequirements.avgMessageSize.toBytes())
                        .toLongCheckOverflow("producedBytesPerDay"),
                diskUsagePerPartitionReplica = diskUsagePerPartitionReplica,
                diskUsagePerBroker = brokerUsage?.diskUsagePerBroker,
                totalDiskUsageBytes = totalDiskUsageBytes,
                partitionInBytesPerSec = partitionInBytesPerSec,
                partitionSyncOutBytesPerSec = partitionSyncOutBytesPerSec,
                brokerProducerInBytesPerSec = brokerUsage?.brokerProducerInBytesPerSec,
                brokerInBytesPerSec = brokerUsage?.let { with(it) { brokerProducerInBytesPerSec + brokerSyncBytesPerSec } },
                brokerSyncBytesPerSec = brokerUsage?.brokerSyncBytesPerSec
        )
    }

    private fun Double.toLongCheckOverflow(what: String): Long {
        return checkOverflow(what) {
            toLong().also {
                if (it == Long.MAX_VALUE) {
                    throw ArithmeticException()
                }
            }
        }
    }

    private fun checkOverflow(what: String, operation: () -> Long): Long {
        return try {
            operation()
        } catch (ex: ArithmeticException) {
            throw ArithmeticException("Value for '$what' overflows MAX_LONG value")
        }
    }

    private data class BrokerUsage(
        val numBrokers: Int,
        val diskUsagePerBroker: Long,
        val brokerProducerInBytesPerSec: Double,
        val brokerSyncBytesPerSec: Double
    )

}

// L=leader
// F=follower
//
// producer in -> [L] -> out-F1, out-F2
//                [F1]
//                [F2]