package com.infobip.kafkistry.service.consume

import com.infobip.kafkistry.kafka.Partition
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.KafkistryUnsupportedOperationException
import com.infobip.kafkistry.service.consume.serialize.KeySerializer
import com.infobip.kafkistry.service.consume.serialize.KeySerializerType
import org.apache.kafka.clients.producer.internals.BuiltInPartitioner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("app.consume.enabled", matchIfMissing = true)
class TopicPartitionResolver(
    keySerializers: List<KeySerializer>,
) {

    private val serializers = keySerializers.associateBy { it.type }

    fun resolvePartition(
        topicName: TopicName,
        key: String,
        serializerType: KeySerializerType,
        partitions: Int,
    ): Partition {
        val serializer = serializers[serializerType]
            ?: throw KafkistryUnsupportedOperationException("Unknown key serializer type: '$serializerType'")
        val keyBytes = serializer.serialize(topicName, key)
        return BuiltInPartitioner.partitionForKey(keyBytes, partitions)
    }
}


