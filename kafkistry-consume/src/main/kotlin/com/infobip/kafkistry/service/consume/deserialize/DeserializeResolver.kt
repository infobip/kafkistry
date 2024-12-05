package com.infobip.kafkistry.service.consume.deserialize

import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.KafkistryPermissionException
import com.infobip.kafkistry.service.consume.DeserializedValue
import com.infobip.kafkistry.service.consume.RecordDeserialization
import com.infobip.kafkistry.service.consume.masking.RecordMasker
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

data class Deserializers(
    val suppressions: Map<DeserializerType, List<DeserializerType>>,
    val kafkaDeserializers: List<KafkaDeserializer>,
    val failoverDeserializers: List<KafkaDeserializer>,
)

interface DeserializeResolverSelector {
    fun keyDeserializerFor(record: ConsumerRecord<*, *>): DeserializerType? = null
    fun valueDeserializerFor(record: ConsumerRecord<*, *>): DeserializerType? = null
    fun headerDeserializerFor(record: ConsumerRecord<*, *>, headerKey: String): DeserializerType? = null
}

@Component
class DeserializeResolver(
    private val availableDeserializers: List<KafkaDeserializer>,
    private val selectors: List<DeserializeResolverSelector>,
) {

    private val allDeserializers = availableDeserializers.associateBy { it.typeName() }

    fun getDeserializersHolder(config: RecordDeserialization, masker: RecordMasker): Holder {
        return Holder(config, masker)
    }

    private fun selectDeserializers(preferredTypes: List<DeserializerType>, needMasking: Boolean): Deserializers {
        val suppressibleByAnything = allDeserializers.filterValues { it.suppressibleByAnything() }.keys
        return if (preferredTypes.isNotEmpty()) {
            val preferredDeserializers = preferredTypes.map {
                allDeserializers[it] ?: throw IllegalArgumentException("Unknown deserializer with name '$it'")
            }
            if (needMasking) {
                preferredDeserializers.forEach {
                    if (!it.isResultMaskable()) {
                        throw KafkistryPermissionException(
                            "Not allowed to use deserializer with name '${it.typeName()}' which deserialization type is not maskable. " +
                                "Reason is that record value must be masked because this topic contains sensitive data."
                        )
                    }
                }
            }
            Deserializers(
                suppressions = preferredDeserializers.associate { deserializer ->
                    deserializer.typeName() to
                    (deserializer.suppresses() + suppressibleByAnything.takeIf { deserializer.typeName() !in it }.orEmpty()).distinct()
                },
                kafkaDeserializers = preferredDeserializers.filter { !needMasking || it.isResultMaskable() },
                failoverDeserializers = listOfNotNull(allDeserializers["STRING"], allDeserializers["BYTES"]),
            )
        } else {
            Deserializers(
                suppressions = allDeserializers.mapValues { (typeName, deserializer) ->
                    (deserializer.suppresses() + suppressibleByAnything.takeIf { typeName !in it }.orEmpty()).distinct()
                },
                kafkaDeserializers = allDeserializers.values.filter { !needMasking || it.isResultMaskable() },
                failoverDeserializers = allDeserializers.values.filter { needMasking && !it.isResultMaskable() },
            )
        }
    }

    fun isSolidCandidate(deserializedValue: DeserializedValue): Boolean {
        return allDeserializers[deserializedValue.typeTag]?.isSolidCandidate(deserializedValue.value) ?: false
    }

    inner class Holder(
        private val recordDeserialization: RecordDeserialization,
        private val masker: RecordMasker,
    ) {

        private val keyDeserializers: MutableMap<TopicName, Deserializers> = ConcurrentHashMap()
        private val valueDeserializers: MutableMap<TopicName, Deserializers> = ConcurrentHashMap()
        private val headerDeserializers: MutableMap<Pair<TopicName, String>, Deserializers> = ConcurrentHashMap()

        private fun selectForKey(record: ConsumerRecord<*, *>): Deserializers {
            val preferred = listOfNotNull(recordDeserialization.keyType).takeIf { it.isNotEmpty() }
                ?: selectors.mapNotNull { it.keyDeserializerFor(record) }.takeIf { it.isNotEmpty() }
                ?: emptyList()
            return selectDeserializers(preferred, masker.masksKey())
        }

        private fun selectForValue(record: ConsumerRecord<*, *>): Deserializers {
            val preferred = listOfNotNull(recordDeserialization.valueType).takeIf { it.isNotEmpty() }
                ?: selectors.mapNotNull { it.valueDeserializerFor(record) }.takeIf { it.isNotEmpty() }
                ?: emptyList()
            return selectDeserializers(preferred, masker.masksValue())
        }

        private fun selectForHeader(record: ConsumerRecord<*, *>, name: String): Deserializers {
            val preferred = listOfNotNull(recordDeserialization.headersType).takeIf { it.isNotEmpty() }
                ?: selectors.mapNotNull { it.headerDeserializerFor(record, name) }.takeIf { it.isNotEmpty() }
                ?: emptyList()
            return selectDeserializers(preferred, masker.masksHeader(name))
        }

        fun keyDeserializers(record: ConsumerRecord<*, *>): Deserializers =
            keyDeserializers.computeIfAbsent(record.topic()) { selectForKey(record) }

        fun valueDeserializers(record: ConsumerRecord<*, *>): Deserializers =
            valueDeserializers.computeIfAbsent(record.topic()) { selectForValue(record) }

        fun headerDeserializers(record: ConsumerRecord<*, *>, name: String): Deserializers =
            headerDeserializers.computeIfAbsent(record.topic() to name) { selectForHeader(record, name) }

    }
}