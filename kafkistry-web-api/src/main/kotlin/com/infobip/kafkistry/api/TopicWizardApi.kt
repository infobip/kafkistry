package com.infobip.kafkistry.api

import com.infobip.kafkistry.model.TopicCreationWizardAnswers
import com.infobip.kafkistry.model.TopicNameMetadata
import com.infobip.kafkistry.service.topic.TopicWizardService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import jakarta.servlet.http.HttpSession

@RestController
@RequestMapping("\${app.http.root-path}/api/topic-wizard")
class TopicWizardApi(
    private val topicWizardService: TopicWizardService
) {

    @PostMapping("/submit-answers")
    fun submitAnswers(
        @RequestBody topicCreationWizardAnswers: TopicCreationWizardAnswers,
        session: HttpSession,
    ) {
        val topicDescription = topicWizardService.generateTopicDescriptionFromWizardAnswers(topicCreationWizardAnswers)
        session.setAttribute(SessionKeys.TOPIC_DESCRIPTION_FROM_WIZARD, topicDescription)
    }

    @PostMapping("/generate-topic-name")
    fun generateTopicName(
        @RequestBody topicNameMetadata: TopicNameMetadata
    ): String = topicWizardService.generateTopicName(topicNameMetadata)

    class SessionKeys {
        companion object {
            const val TOPIC_DESCRIPTION_FROM_WIZARD = "TOPIC_DESCRIPTION_FROM_WIZARD"
        }
    }
}