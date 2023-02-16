package com.infobip.kafkistry.service.topic.inspectors

import com.infobip.kafkistry.kafkastate.KafkaConsumerGroupsProvider
import com.infobip.kafkistry.service.StatusLevel
import com.infobip.kafkistry.service.consumers.ConsumersService
import com.infobip.kafkistry.service.topic.*
import com.infobip.kafkistry.service.topic.offsets.TopicOffsetsService
import org.springframework.stereotype.Component

val NO_CONSUMERS = TopicInspectionResultType(
    name = "NO_CONSUMERS",
    level = StatusLevel.WARNING,
    category = IssueCategory.NONE,
    doc = "Topic has no consumers reading it",
)

@Component
class TopicNoConsumersInspector(
    private val consumerGroupsProvider: KafkaConsumerGroupsProvider,
) : TopicExternalInspector {

    override fun inspectTopic(
        ctx: TopicInspectCtx,
        outputCallback: TopicExternalInspectCallback
    ) {
        if (ctx.existingTopic == null) {
            return  //topic doesn't exist
        }
        if (ctx.existingTopic.internal) {
            return  //don't expect regular consumers reading from kafka's internal topic
        }
        val clusterGroups = consumerGroupsProvider.getLatestState(ctx.clusterRef.identifier)
            .valueOrNull()
            ?: return   //no data from cluster
        val groups = clusterGroups.topicConsumerGroups[ctx.topicName].orEmpty()
        if (groups.isEmpty()) {
            outputCallback.addStatusType(NO_CONSUMERS)
        }
    }
}
