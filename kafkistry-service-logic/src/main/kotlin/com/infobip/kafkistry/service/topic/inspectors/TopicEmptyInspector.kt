package com.infobip.kafkistry.service.topic.inspectors

import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.StatusLevel
import com.infobip.kafkistry.service.topic.IssueCategory
import com.infobip.kafkistry.service.topic.TopicExternalInspectCallback
import com.infobip.kafkistry.service.topic.TopicExternalInspector
import com.infobip.kafkistry.service.topic.TopicInspectionResultType
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
        topicName: TopicName,
        clusterRef: ClusterRef,
        outputCallback: TopicExternalInspectCallback
    ) {
        val empty = topicOffsetsService.topicOffsets(clusterRef.identifier, topicName)?.empty
            ?: return
        if (empty) outputCallback.addStatusType(EMPTY)
    }
}