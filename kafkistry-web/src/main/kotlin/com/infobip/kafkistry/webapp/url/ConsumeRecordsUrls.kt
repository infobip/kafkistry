package com.infobip.kafkistry.webapp.url

import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import org.springframework.web.bind.annotation.RequestParam

class ConsumeRecordsUrls(base: String) : BaseUrls() {

    companion object {
        const val CONSUME = "/consume"
        const val CONSUME_READ_TOPIC = "/read-topic"
        const val CONSUME_READ_TOPIC_CONTINUE = "/read-topic/continue"
    }

    private val showConsume = Url(base, listOf(
        "topicName", "clusterIdentifier",
        "numRecords", "maxWaitMs", "waitStrategy", "offsetType", "offset", "partition", "readFilterJson",
        "readOnlyCommitted"
    ))
    private val showReadRecords = Url("$base$CONSUME_READ_TOPIC", listOf("clusterIdentifier", "topicName"))
    private val showReadRecordsContinue = Url("$base$CONSUME_READ_TOPIC_CONTINUE", listOf("clusterIdentifier", "topicName"))

    fun showConsumePage() = showConsume.render()

    fun showConsumePage(
        topicName: TopicName,
        clusterIdentifier: KafkaClusterIdentifier
    ) = showConsumePage(
        topicName, clusterIdentifier,
        null, null, null, null, null, null, null,
        null, null, null,
    )

    @SuppressWarnings("kotlin:S107")
    fun showConsumePage(
        topicName: TopicName?,
        clusterIdentifier: KafkaClusterIdentifier?,
        numRecords: Int?,
        maxWaitMs: Long?,
        waitStrategy: String?,
        offsetType: String?,
        offset: Long?,
        partition: Int?,
        readFilterJson: String?,
        readOnlyCommitted: Boolean?,
        autoContinuation: Boolean?,
        autoContinuationAfterEnd: Boolean?,
    ) = showConsume.render(
        "topicName" to topicName,
        "clusterIdentifier" to clusterIdentifier,
        "numRecords" to numRecords?.toString(),
        "maxWaitMs" to maxWaitMs?.toString(),
        "waitStrategy" to waitStrategy,
        "offsetType" to offsetType,
        "offset" to offset?.toString(),
        "partition" to partition?.toString(),
        "readFilterJson" to readFilterJson,
        "readOnlyCommitted" to readOnlyCommitted?.toString(),
        "autoContinuation" to autoContinuation?.toString(),
        "autoContinuationAfterEnd" to autoContinuationAfterEnd?.toString(),
    )

    fun showReadRecords(
        clusterIdentifier: KafkaClusterIdentifier,
        topicName: TopicName
    ) = showReadRecords.render(
        "clusterIdentifier" to clusterIdentifier,
        "topicName" to topicName
    )

    fun showReadRecordsContinue(
        clusterIdentifier: KafkaClusterIdentifier,
        topicName: TopicName
    ) = showReadRecordsContinue.render(
        "clusterIdentifier" to clusterIdentifier,
        "topicName" to topicName
    )
}