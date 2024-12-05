package com.infobip.kafkistry.service.consume

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.consume.deserialize.DeserializeResolver
import org.apache.kafka.clients.consumer.ConsumerRecord
import com.infobip.kafkistry.service.consume.deserialize.DeserializerType
import com.infobip.kafkistry.service.consume.deserialize.Deserializers
import com.infobip.kafkistry.service.consume.deserialize.KafkaDeserializer
import com.infobip.kafkistry.service.consume.masking.RecordMasker
import com.infobip.kafkistry.service.consume.masking.RecordMaskerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.*

@Component
@ConditionalOnProperty("app.consume.enabled", matchIfMissing = true)
class RecordFactory(
    private val deserializeResolver: DeserializeResolver,
    private val recordMaskerFactory: RecordMaskerFactory,
) {

    private val jsonSerializer = jacksonObjectMapper()

    interface Creator {
        fun create(consumerRecord: ConsumerRecord<ByteArray, ByteArray>): KafkaRecord
    }

    fun creatorFor(
        topicName: TopicName,
        clusterRef: ClusterRef,
        recordDeserialization: RecordDeserialization,
    ): Creator {
        val masker = recordMaskerFactory.createMaskerFor(topicName, clusterRef)
        val deserializeHolder = deserializeResolver.getDeserializersHolder(recordDeserialization, masker)
        return object : Creator {
            override fun create(consumerRecord: ConsumerRecord<ByteArray, ByteArray>): KafkaRecord {
                return with(consumerRecord) {
                    val key = deserializeKey(deserializeHolder, masker)
                    val value = deserializeValue(deserializeHolder, masker)
                    val headers = deserializeHeaders(deserializeHolder, masker)
                    KafkaRecord(
                        topic = topic(),
                        key = key,
                        partition = partition(),
                        timestamp = timestamp(),
                        timestampType = timestampType(),
                        offset = offset(),
                        leaderEpoch = leaderEpoch().orElse(null),
                        value = value,
                        headers = headers,
                        keySize = key()?.size ?: 0,
                        valueSize = value()?.size ?: 0,
                        headersSize = headers().sumOf { it.key().length + (it.value()?.size ?: 0) },
                    )
                }
            }
        }
    }

    private fun ConsumerRecord<ByteArray, ByteArray>.deserializeKey(deserializers: DeserializeResolver.Holder, masker: RecordMasker): KafkaValue {
        return key().doDeserialize(
            deserializers = deserializers.keyDeserializers(this),
            deserializeOp = { deserializeKey(it, this@deserializeKey) },
            deserializerSupports = { supportsKey(topic()) },
            maskerMasks = masker.masksKey(),
            maskOp = masker::maskKey,
        )
    }

    private fun ConsumerRecord<ByteArray, ByteArray>.deserializeValue(deserializers: DeserializeResolver.Holder, masker: RecordMasker): KafkaValue {
        return value().doDeserialize(
            deserializers = deserializers.valueDeserializers(this),
            deserializeOp = { deserializeValue(it, this@deserializeValue) },
            deserializerSupports = { supportsValue(topic()) },
            maskerMasks = masker.masksValue(),
            maskOp = masker::maskValue,
        )
    }

    private fun ConsumerRecord<ByteArray, ByteArray>.deserializeHeaders(deserializers: DeserializeResolver.Holder, masker: RecordMasker): List<RecordHeader> {
        return headers().map { header ->
            RecordHeader(
                key = header.key(),
                value = header.value().doDeserialize(
                    deserializers = deserializers.headerDeserializers(this, header.key()),
                    deserializeOp = { deserializeHeader(it, header.key(),this@deserializeHeaders) },
                    deserializerSupports = { supportsHeader(topic(), header.key()) },
                    maskerMasks = masker.masksHeader(header.key()),
                    maskOp = { masker.maskHeader(header.key(), it) },
                ),
            )
        }
    }

    private fun ByteArray?.doDeserialize(
        deserializers: Deserializers,
        deserializerSupports: KafkaDeserializer.() -> Boolean,
        deserializeOp: KafkaDeserializer.(ByteArray) -> DeserializedValue?,
        maskerMasks: Boolean,
        maskOp: (Any?) -> Any?,
    ): KafkaValue {
        val value = this ?: return KafkaValue.NULL
        if (value.isEmpty()) return KafkaValue.EMPTY
        fun List<KafkaDeserializer>.tryDeserialize(): Map<DeserializerType, DeserializedValue> {
            return this
                .filter { it.deserializerSupports() }
                .mapNotNull { it.deserializeOp(value) }
                .maybeMask(maskerMasks, maskOp)
                .associateBy { it.typeTag }
                .suppress(deserializers)
        }
        val deserializations = deserializers.kafkaDeserializers.tryDeserialize()
            // value can't deserialize with any of "safe" maskable deserializers
            // failover to deserializers which produce un-maskable value in order to be able to "see" broken values
            .takeIf { it.isNotEmpty() || !maskerMasks }
            ?: deserializers.failoverDeserializers.tryDeserialize()
        val masked = deserializations.any { it.value.isMasked }
        return KafkaValue(
            isMasked = masked,
            rawBase64Bytes = if (masked) null else Base64.getEncoder().encodeToString(value),
            deserializations = deserializations,
        )
    }

    private fun Map<DeserializerType, DeserializedValue>.suppress(
        deserializers: Deserializers
    ): Map<DeserializerType, DeserializedValue> {
        return if (deserializers.kafkaDeserializers.size > 1) {
            filter { deserializeResolver.isSolidCandidate(it.value) }.doSuppress(deserializers)
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
        return mapValues {
            if (it.key in suppressedTypes) {
                it.value.copy(isSuppressed = true)
            } else {
                it.value
            }
        }
    }

    private inline fun List<DeserializedValue>.maybeMask(masks: Boolean, maskOp: (Any?) -> Any?): List<DeserializedValue> {
        return if (masks) map { it.maskWith(maskOp) } else this
    }

    private inline fun DeserializedValue.maskWith(maskOp: (Any?) -> Any?): DeserializedValue {
        val maskedValue = maskOp(asFilterable)
        val masked = maskedValue !== asFilterable
        if (!masked) {
            return this
        }
        return copy(
            value = maskedValue ?: "[NULL]",
            asFilterable = maskedValue ?: "[NULL]",
            asJson = jsonSerializer.writeValueAsString(maskedValue),
            isMasked = true,
        )
    }

}
