package com.infobip.kafkistry.webapp.url

import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName

class ProduceRecordsUrls(base: String) : BaseUrls() {

    companion object {
        const val PRODUCE = "/produce"
        const val PRODUCE_SEND_RECORD = "/send-record"
    }

    private val showProduce = Url(base, listOf(
        "topicName", "clusterIdentifier"
    ))
    private val sendRecord = Url("$base$PRODUCE_SEND_RECORD", listOf(
        "clusterIdentifier", "topicName"
    ))

    fun showProducePage() = showProduce.render()

    fun showProducePage(
        topicName: TopicName?,
        clusterIdentifier: KafkaClusterIdentifier?
    ) = showProduce.render(
        "topicName" to topicName,
        "clusterIdentifier" to clusterIdentifier
    )

    fun sendRecord(
        clusterIdentifier: KafkaClusterIdentifier,
        topicName: TopicName
    ) = sendRecord.render(
        "clusterIdentifier" to clusterIdentifier,
        "topicName" to topicName
    )
}
