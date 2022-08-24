package com.infobip.kafkistry.service.topic.inspectors

import com.infobip.kafkistry.service.StatusLevel
import com.infobip.kafkistry.service.topic.*
import com.infobip.kafkistry.service.topic.offsets.TopicOffsetsService
import org.springframework.stereotype.Component

val EMPTY = TopicInspectionResultType(
    name = "EMPTY",
    level = StatusLevel.WARNING,
    category = IssueCategory.NONE,
    doc = "Topic has no data in any partition",
)

@Component
class TopicEmptyInspector(
    private val topicOffsetsService: TopicOffsetsService,
) : TopicExternalInspector {

    override fun inspectTopic(
        ctx: TopicInspectCtx,
        outputCallback: TopicExternalInspectCallback
    ) {
        val topicOffsets = ctx.cache { topicOffsetsService.topicOffsets(clusterRef.identifier, topicName) }
            ?: return
        if (topicOffsets.empty) {
            outputCallback.addStatusType(EMPTY)
        }
    }
}