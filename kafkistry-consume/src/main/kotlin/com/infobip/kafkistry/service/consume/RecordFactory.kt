package com.infobip.kafkistry.service.consume

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.KafkistryPermissionException
import org.apache.kafka.clients.consumer.ConsumerRecord
import com.infobip.kafkistry.service.consume.deserialize.DeserializerType
import com.infobip.kafkistry.service.consume.deserialize.KafkaDeserializer
import com.infobip.kafkistry.service.consume.masking.RecordMasker
import com.infobip.kafkistry.service.consume.masking.RecordMaskerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.*

@Component
@ConditionalOnProperty("app.consume.enabled", matchIfMissing = true)
class RecordFactory(
    availableDeserializers: List<KafkaDeserializer>,
    private val recordMaskerFactory: RecordMaskerFactory,
) {

    private val jsonSerializer = jacksonObjectMapper()
    private val allDeserializers = availableDeserializers.associateBy { it.typeName() }

    interface Creator {
        fun create(consumerRecord: ConsumerRecord<ByteArray, ByteArray>): KafkaRecord
    }

    fun creatorFor(
        topicName: TopicName,
        clusterIdentifier: KafkaClusterIdentifier,
        recordDeserialization: RecordDeserialization,
    ): Creator {
        val masker = recordMaskerFactory.createMaskerFor(topicName, clusterIdentifier)
        val keyDeserializers = selectDeserializers(recordDeserialization.keyType, masker.masksKey())
        val valueDeserializers = selectDeserializers(recordDeserialization.valueType, masker.masksValue())
        val headersDeserializers = selectDeserializers(recordDeserialization.headersType, masker.masksHeader())
        return object : Creator {
            override fun create(consumerRecord: ConsumerRecord<ByteArray, ByteArray>): KafkaRecord {
                return with(consumerRecord) {
                    val key = deserializeKey(keyDeserializers, masker)
                    val value = deserializeValue(valueDeserializers, masker)
                    val headers = deserializeHeaders(headersDeserializers, masker)
                    KafkaRecord(
                        topic = topic(),
                        key = key,
                        partition = partition(),
                        timestamp = timestamp(),
                        offset = offset(),
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

    private fun selectDeserializers(typeName: DeserializerType?, needMasking: Boolean): Deserializers {
        return if (typeName == null) {
            val suppressibleByAnything = allDeserializers.filterValues { it.suppressibleByAnything() }.keys
            Deserializers(
                suppressions = allDeserializers.mapValues { (typeName, deserializer) ->
                    (deserializer.suppresses() + suppressibleByAnything.takeIf { typeName !in it }.orEmpty()).distinct()
                },
                kafkaDeserializers = allDeserializers.values.filter { !needMasking || it.isResultMaskable() },
                failoverDeserializers = allDeserializers.values.filter { needMasking && !it.isResultMaskable() },
            )
        } else {
            val deserializer = allDeserializers[typeName]
                ?: throw IllegalArgumentException("Unknown deserializer with name '$typeName'")
            if (needMasking && !deserializer.isResultMaskable()) {
                throw KafkistryPermissionException(
                    "Not allowed to use deserializer with name '$typeName' which deserialization type is not maskable. " +
                            "Reason is that record value must be masked because this topic contains sensitive data."
                )
            }
            Deserializers(
                suppressions = emptyMap(),
                kafkaDeserializers = listOf(deserializer),
                failoverDeserializers = listOfNotNull(allDeserializers["STRING"], allDeserializers["BYTES"]),
            )
        }
    }

    private data class Deserializers(
        val suppressions: Map<DeserializerType, List<DeserializerType>>,
        val kafkaDeserializers: List<KafkaDeserializer>,
        val failoverDeserializers: List<KafkaDeserializer>,
    )

    private fun ConsumerRecord<ByteArray, ByteArray>.deserializeKey(deserializers: Deserializers, masker: RecordMasker): KafkaValue {
        return key().doDeserialize(
            deserializers = deserializers,
            deserializeOp = { deserializeKey(it, this@deserializeKey) },
            deserializerSupports = { supportsKey(topic()) },
            maskerMasks = masker.masksKey(),
            maskOp = masker::maskKey,
        )
    }

    private fun ConsumerRecord<ByteArray, ByteArray>.deserializeValue(deserializers: Deserializers, masker: RecordMasker): KafkaValue {
        return value().doDeserialize(
            deserializers = deserializers,
            deserializeOp = { deserializeValue(it, this@deserializeValue) },
            deserializerSupports = { supportsValue(topic()) },
            maskerMasks = masker.masksValue(),
            maskOp = masker::maskValue,
        )
    }

    private fun ConsumerRecord<ByteArray, ByteArray>.deserializeHeaders(deserializers: Deserializers, masker: RecordMasker): List<RecordHeader> {
        return headers().map { header ->
            RecordHeader(
                key = header.key(),
                value = header.value().doDeserialize(
                    deserializers = deserializers,
                    deserializeOp = { deserializeHeader(it, header.key(),this@deserializeHeaders) },
                    deserializerSupports = { supportsHeader(topic(), header.key()) },
                    maskerMasks = masker.masksHeader(),
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