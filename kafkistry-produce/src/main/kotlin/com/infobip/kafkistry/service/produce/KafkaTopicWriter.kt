package com.infobip.kafkistry.service.produce

import com.infobip.kafkistry.kafka.ClientFactory
import com.infobip.kafkistry.kafka.connectionDefinition
import com.infobip.kafkistry.model.KafkaCluster
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.produce.config.ProduceProperties
import com.infobip.kafkistry.service.produce.serialize.SerializerResolver
import com.infobip.kafkistry.utils.deepToString
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.header.internals.RecordHeader
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("app.produce.enabled", matchIfMissing = false)
class KafkaTopicWriter(
    private val produceProperties: ProduceProperties,
    private val serializerResolver: SerializerResolver,
    private val clientFactory: ClientFactory
) {

    fun produceRecord(
        topic: TopicName,
        cluster: KafkaCluster,
        username: String,
        produceRequest: ProduceRequest,
    ): ProduceResult {
        try {
            // Validate and serialize
            val keyBytes = produceRequest.key?.serialize(topic)
            val valueBytes = produceRequest.value?.serialize(topic)
            val headers = produceRequest.headers.map { header ->
                RecordHeader(header.key, header.value?.serialize(topic))
            }.toMutableList()

            // Inject username header if enabled
            if (produceProperties.injectUsernameHeader) {
                headers.add(RecordHeader(produceProperties.usernameHeaderName, username.toByteArray(Charsets.UTF_8)))
            }

            // Create producer and send
            return createProducer(cluster, username).use { producer ->
                val record = ProducerRecord(
                    topic,
                    produceRequest.partition,
                    produceRequest.timestamp,
                    keyBytes,
                    valueBytes,
                    headers,
                )

                val metadata: RecordMetadata = producer.send(record).get()

                ProduceResult(
                    success = true,
                    topic = metadata.topic(),
                    partition = metadata.partition(),
                    offset = metadata.offset(),
                    timestamp = metadata.timestamp()
                )
            }
        } catch (ex: Exception) {
            return ProduceResult(
                success = false,
                topic = topic,
                partition = produceRequest.partition ?: -1,
                offset = -1L,
                timestamp = -1L,
                errorMessage = ex.deepToString(),
            )
        }
    }

    private fun ProduceValue.serialize(topic: TopicName) = serializerResolver
        .resolve(serializerType)
        .serialize(topic, content)

    private fun createProducer(
        cluster: KafkaCluster, username: String,
    ): KafkaProducer<ByteArray, ByteArray> {
        return clientFactory.createProducer(cluster.connectionDefinition()) { props ->
            props[ProducerConfig.CLIENT_ID_CONFIG] = "kafkistry-producer-$username"
            props[ProducerConfig.ACKS_CONFIG] = "all"
            props[ProducerConfig.RETRIES_CONFIG] = "3"
            props[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = "1"
            props[ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG] = "${produceProperties.requestTimeoutMs()}"
            props[ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG] = "${produceProperties.deliveryTimeoutMs()}"
        }
    }
}
