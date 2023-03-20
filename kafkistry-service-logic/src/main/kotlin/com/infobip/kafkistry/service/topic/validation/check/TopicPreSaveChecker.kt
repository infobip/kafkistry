package com.infobip.kafkistry.service.topic.validation.check

import com.infobip.kafkistry.model.TopicDescription

interface TopicPreSaveChecker {

    fun check(topicDescription: TopicDescription)

    fun preCreateCheck(topicDescription: TopicDescription) = check(topicDescription)
    fun preUpdateCheck(topicDescription: TopicDescription) = check(topicDescription)
}
