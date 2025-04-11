package com.infobip.kafkistry.it.cluster_ops

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import com.infobip.kafkistry.kafka.KafkaManagementClient
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException
import java.time.Duration

fun <K, V> KafkaConsumer<K, V>.poolAll(): List<ConsumerRecord<K, V>> {
    return sequence<ConsumerRecord<K, V>> {
        poll(Duration.ofSeconds(5)).also {
            yieldAll(it)
        }
        while (true) {
            val numberOfMessages = poll(Duration.ofSeconds(2)).also {
                yieldAll(it)
            }.count()
            if (numberOfMessages == 0) {
                break
            }
        }
    }.toList()
}
