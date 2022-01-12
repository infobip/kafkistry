package com.infobip.kafkistry.kafka.recordsampling

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.springframework.stereotype.Component

@Component
class RecordReadSamplerFactory {

    companion object {
        val consumerProperties = mapOf(
            ConsumerConfig.GROUP_ID_CONFIG to "kafkistry-oldest-age-monitor",
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1",          //read only one record from partition
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "false",    //assignment and seeking is fully managed by RecordReadSampler
            ConsumerConfig.FETCH_MAX_BYTES_CONFIG to "1",
            ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG to "1", //force broker to return only 1 record
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
        )
    }

    fun createReader(
        consumerFactory: (Map<String, String>) -> KafkaConsumer<ByteArray, ByteArray>
    ): RecordReadSampler {
        return RecordReadSampler(
            consumer = consumerFactory(consumerProperties),
            initialPoolTimeout = 10_000L,
            poolTimeout = 1_000L,
        )
    }
}