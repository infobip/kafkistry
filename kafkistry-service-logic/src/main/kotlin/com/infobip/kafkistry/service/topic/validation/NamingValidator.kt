package com.infobip.kafkistry.service.topic.validation

import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.KafkistryValidationException
import org.springframework.stereotype.Component

@Component
class NamingValidator {

    private val namePattern = Regex("""\w+([\w\-.]+\w+)?""")

    fun validateTopicName(topicName: TopicName) = validateName(topicName)

    fun validateClusterIdentifier(clusterIdentifier: KafkaClusterIdentifier) = validateName(clusterIdentifier)

    private fun validateName(name: String) {
        if (!namePattern.matches(name)) {
            throw KafkistryValidationException(
                    "Name '$name' is not valid name, name is allowed to contain only ascii " +
                            "letters, digits, underscore and dot/minus except on start/end"
            )
        }
    }

}