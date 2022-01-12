package com.infobip.kafkistry.it.cluster_ops

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import com.infobip.kafkistry.kafka.KafkaManagementClient
import java.time.Duration

fun <K, V> KafkaConsumer<K, V>.poolAll(maxIterations: Int = 10): List<ConsumerRecord<K, V>> {
    val result = mutableListOf<ConsumerRecord<K, V>>()
    var i = 0
    while (true) {
        i++
        val records = poll(Duration.ofSeconds(1))
        if (records.isEmpty || i >= maxIterations) {
            break
        }
        records.forEach { result.add(it) }
    }
    return result
}

fun KafkaManagementClient.deleteAllOnCluster() {
    val topics = listAllTopicNames().get()
    topics.forEach { deleteTopic(it).get() }
    val groups = consumerGroups().get()
    groups.forEach { deleteConsumer(it).exceptionally {  }.get() }
    try {
        val acls = listAcls().get()
        deleteAcls(acls).get()
    } catch (_: Exception) {
        //security disabled, ignore
    }
    //try to ensure that all topics are deleted
    for (iteration in 1..10) {
        val constantlyReportsAllDeleted = (1..6).all {
            Thread.sleep(100)
            val noTopics = try {
                listAllTopics().get().isEmpty()
            } catch (e: Exception) {
                false
            }
            val noConsumerGroups = consumerGroups().get().isEmpty()
            val noAcls = try {
                listAcls().get().isEmpty()
            } catch (_: Exception) {
                true
            }
            noTopics && noConsumerGroups && noAcls
        }
        if (constantlyReportsAllDeleted) {
            break
        }
        Thread.sleep(1000)
    }

}