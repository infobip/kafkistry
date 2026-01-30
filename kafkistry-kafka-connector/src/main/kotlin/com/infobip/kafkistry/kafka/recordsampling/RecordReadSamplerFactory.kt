package com.infobip.kafkistry.kafka.recordsampling

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@Configuration
@ConfigurationProperties(prefix = "app.kafka.sampling")
class KafkaRecordSamplerProperties {

    var initialPoolTimeoutMs: Long = 10_000L
    var poolTimeoutMs: Long = 1_000L
}


@Component
class RecordReadSamplerFactory(
    private val properties: KafkaRecordSamplerProperties,
) {

    companion object {
        val consumerProperties = mapOf(
            ConsumerConfig.GROUP_ID_CONFIG to "kafkistry-oldest-age-monitor",
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1",          //read only one record from partition
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "false",    //assignment and seeking is fully managed by RecordReadSampler
            ConsumerConfig.FETCH_MAX_BYTES_CONFIG to "1",           //we need only one record per partition
            ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG to "1",         //minimum waiting, don't need to wait for accumulation
            ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG to "1", //force broker to return only 1 record
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ISOLATION_LEVEL_CONFIG to "read_uncommitted",
        )
    }

    fun createReader(
        consumerFactory: (Map<String, String>) -> KafkaConsumer<ByteArray, ByteArray>
    ): RecordReadSampler {
        return RecordReadSampler(
            consumer = consumerFactory(consumerProperties),
            properties = properties,
        )
    }
}