package com.infobip.kafkistry.service.consume

import org.apache.kafka.clients.consumer.ConsumerRecord
import com.infobip.kafkistry.service.consume.deserialize.DeserializerType
import com.infobip.kafkistry.service.consume.deserialize.KafkaDeserializer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.*

@Component
@ConditionalOnProperty("app.consume.enabled", matchIfMissing = true)
class RecordFactory(
    availableDeserializers: List<KafkaDeserializer>,
) {

    private val allDeserializers = availableDeserializers.associateBy { it.typeName() }

    interface Creator {
        fun create(consumerRecord: ConsumerRecord<ByteArray, ByteArray>): KafkaRecord
    }

    fun creatorFor(recordDeserialization: RecordDeserialization): Creator {
        val keyDeserializers = selectDeserializers(recordDeserialization.keyType)
        val valueDeserializers = selectDeserializers(recordDeserialization.valueType)
        val headersDeserializers = selectDeserializers(recordDeserialization.headersType)
        return object : Creator {
            override fun create(consumerRecord: ConsumerRecord<ByteArray, ByteArray>): KafkaRecord {
                return with(consumerRecord) {
                    KafkaRecord(
                        topic = topic(),
                        key = deserializeKey(keyDeserializers),
                        partition = partition(),
                        timestamp = timestamp(),
                        offset = offset(),
                        value = deserializeValue(valueDeserializers),
                        headers = extractRecordHeaders(headersDeserializers),
                        keySize = key()?.size ?: 0,
                        valueSize = value()?.size ?: 0,
                        headersSize = headers().sumOf { it.key().length + (it.value()?.size ?: 0) },
                    )
                }
            }
        }
    }

    private fun selectDeserializers(typeName: DeserializerType?): Deserializers {
        return if (typeName == null) {
            val suppressableByAnything = allDeserializers.filterValues { it.suppressableByAnything() }.keys
            Deserializers(
                suppressions = allDeserializers.mapValues { (typeName, deserializer) ->
                    (deserializer.suppresses() + suppressableByAnything.takeIf { typeName !in it }.orEmpty()).distinct()
                },
                kafkaDeserializers = allDeserializers.values.toList(),
            )
        } else {
            Deserializers(
                suppressions = emptyMap(),
                kafkaDeserializers = listOf(
                    allDeserializers[typeName]
                        ?: throw IllegalArgumentException("Unknown deserializer with name '$typeName'")
                )
            )
        }
    }

    private data class Deserializers(
        val suppressions: Map<DeserializerType, List<DeserializerType>>,
        val kafkaDeserializers: List<KafkaDeserializer>,
    )

    private fun ConsumerRecord<ByteArray, ByteArray>.deserializeKey(deserializers: Deserializers): KafkaValue {
        val key = key() ?: return KafkaValue.NULL
        if (key.isEmpty()) return KafkaValue.EMPTY
        return KafkaValue(
            rawBase64Bytes = Base64.getEncoder().encodeToString(key),
            deserializations = deserializers.kafkaDeserializers
                .filter { it.supportsKey(topic()) }
                .mapNotNull { it.deserializeKey(key, this) }
                .associateBy { it.typeTag }
                .suppress(deserializers),
        )
    }

    private fun ConsumerRecord<ByteArray, ByteArray>.deserializeValue(deserializers: Deserializers): KafkaValue {
        val value = value() ?: return KafkaValue.NULL
        if (value.isEmpty()) return KafkaValue.EMPTY
        return KafkaValue(
            rawBase64Bytes = Base64.getEncoder().encodeToString(value),
            deserializations = deserializers.kafkaDeserializers
                .filter { it.supportsValue(topic()) }
                .mapNotNull { it.deserializeValue(value, this) }
                .associateBy { it.typeTag }
                .suppress(deserializers),
        )
    }

    private fun ConsumerRecord<ByteArray, ByteArray>.extractRecordHeaders(deserializers: Deserializers): List<RecordHeader> {
        return headers().map { header ->
            RecordHeader(
                key = header.key(),
                value = run {
                    val headerValue = header.value()
                    when {
                        headerValue == null -> KafkaValue.NULL
                        headerValue.isEmpty() -> KafkaValue.EMPTY
                        else -> KafkaValue(
                            rawBase64Bytes = Base64.getEncoder().encodeToString(headerValue),
                            deserializations = deserializers.kafkaDeserializers
                                .filter { it.supportsHeader(topic(), header.key()) }
                                .mapNotNull { it.deserializeHeader(headerValue, header.key(), this) }
                                .associateBy { it.typeTag }
                                .suppress(deserializers)
                        )
                    }
                },
            )
        }
    }

    private fun Map<DeserializerType, DeserializedValue>.suppress(
        deserializers: Deserializers
    ): Map<DeserializerType, DeserializedValue> {
        return if (deserializers.kafkaDeserializers.size > 1) {
            filter { allDeserializers[it.key]!!.isSolidCandidate(it.value.value) }.doSuppress(deserializers)
        } else {
            doSuppress(deserializers)
        }
    }

    private fun Map<DeserializerType, DeserializedValue>.doSuppress(
        deserializers: Deserializers
    ): Map<DeserializerType, DeserializedValue> {
        if (deserializers.suppressions.isEmpty()) return this
        val suppressedTypes = keys.flatMap { deserializers.suppressions[it].orEmpty() }.toSet()
        if (suppressedTypes.isEmpty()) return this
        return filterKeys { it !in suppressedTypes }
    }

}