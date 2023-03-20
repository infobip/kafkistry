package com.infobip.kafkistry.service.topic.validation.check

import com.infobip.kafkistry.model.TopicDescription
import com.infobip.kafkistry.service.KafkistryValidationException
import org.springframework.stereotype.Component

@Component
class TopicMetadataChecker : TopicPreSaveChecker {

    override fun check(topicDescription: TopicDescription) {
        with(topicDescription) {
            if (description.isBlank()) {
                throw KafkistryValidationException("Description must not be blank")
            }
            if (owner.isBlank()) {
                throw KafkistryValidationException("Owner musts not be blank")
            }
        }
    }

}
