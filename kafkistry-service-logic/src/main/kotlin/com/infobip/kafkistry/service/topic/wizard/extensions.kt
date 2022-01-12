package com.infobip.kafkistry.service.topic.wizard

import com.infobip.kafkistry.model.TopicNameMetadata
import com.infobip.kafkistry.service.TopicWizardException

fun TopicNameMetadata.string(attrName: String): String = attr(attrName) { stringOrNull(attrName) }

fun TopicNameMetadata.boolean(attrName: String): Boolean = attr(attrName) { booleanOrNull(attrName) }

fun <E : Enum<E>> TopicNameMetadata.enum(attrName: String, enumType: Class<E>): E = attr(attrName) { enumOrNull(attrName, enumType) }

private fun <T> TopicNameMetadata.attr(attrName: String, extractor: TopicNameMetadata.(String) -> T?): T {
    return extractor(attrName) ?: throw TopicWizardException("Missing attribute '$attrName'")
}


fun TopicNameMetadata.booleanOrNull(attrName: String): Boolean? = stringOrNull(attrName)?.toBoolean()

fun <E : Enum<E>> TopicNameMetadata.enumOrNull(attrName: String, enumType: Class<E>): E? =
        stringOrNull(attrName)?.let { java.lang.Enum.valueOf(enumType, it) }

fun TopicNameMetadata.stringOrNull(attrName: String): String? = attributes[attrName]

