package com.infobip.kafkistry.service.topic.wizard

import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.model.TopicNameMetadata

/**
 * Allows enforcing particular custom naming convention for topics created via topic creation wizard
 */
interface TopicNameGenerator {

    /**
     * Generates topic name based on name metadata attributes from UI's form
     * @return generated topic name
     * @throws com.infobip.kafkistry.service.TopicWizardException if name can't be generated (missing attributes, any kind of violation, ...)
     */
    fun generateTopicName(nameMetadata: TopicNameMetadata): TopicName
}

object DefaultTopicNameGenerator : TopicNameGenerator {

    override fun generateTopicName(nameMetadata: TopicNameMetadata): TopicName {
        return nameMetadata.string("name")
    }

}