package com.infobip.kafkistry.service.topic.inspectors

import com.infobip.kafkistry.model.Label
import com.infobip.kafkistry.service.StatusLevel
import com.infobip.kafkistry.service.existingvalues.ExistingValuesSupplier
import com.infobip.kafkistry.service.topic.*
import com.infobip.kafkistry.service.topic.offsets.TopicOffsetsService
import org.springframework.stereotype.Component

val EMPTY = TopicInspectionResultType(
    name = "EMPTY",
    level = StatusLevel.WARNING,
    category = IssueCategory.NONE,
    doc = "Topic has no data in any partition",
)

val EMPTY_OK = TopicInspectionResultType(
    name = "EMPTY",
    level = StatusLevel.IGNORE,
    category = IssueCategory.NONE,
    doc = "Topic has no data in any partition, and it's normal to be empty",
)

val EXPECTED_EMPTY_LABEL = Label(
    category = "BEHAVIOUR",
    name = "NORMALLY-EMPTY",
)

@Component
class TopicEmptyInspector(
    private val topicOffsetsService: TopicOffsetsService,
) : TopicExternalInspector, ExistingValuesSupplier {

    private val advertisedLabels = listOf(EXPECTED_EMPTY_LABEL)

    override fun inspectTopic(
        ctx: TopicInspectCtx,
        outputCallback: TopicExternalInspectCallback
    ) {
        val topicOffsets = ctx.cache { topicOffsetsService.topicOffsets(clusterRef.identifier, topicName) }
            ?: return
        if (topicOffsets.empty) {
            if (EXPECTED_EMPTY_LABEL in ctx.topicDescription?.labels.orEmpty()) {
                outputCallback.addStatusType(EMPTY_OK)
            } else {
                outputCallback.addStatusType(EMPTY)
            }
        }
    }

    override fun labels(): List<Label> = advertisedLabels
}