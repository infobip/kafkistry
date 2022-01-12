package com.infobip.kafkistry.service.oldestrecordage

import org.apache.kafka.clients.consumer.ConsumerRecord

interface RecordTimestampExtractor {

    /**
     * Try to extract timestamp in milliseconds from consumer record.
     *
     * Implementation is able to extract timestamp from specific headers or even message payload
     * (depending on business meaning).
     * Prefer returning `null` instead of throwing an exception as signal for
     * "unable to extract timestamp from particular record".
     *
     * @return extracted timestamp epoch millis or `null` if unable to extract.
     */
    fun extractTimestampFrom(consumerRecord: ConsumerRecord<ByteArray?, ByteArray?>): Long?

    companion object {
        val NONE = object : RecordTimestampExtractor {
            override fun extractTimestampFrom(consumerRecord: ConsumerRecord<ByteArray?, ByteArray?>): Long? = null
        }
    }
}