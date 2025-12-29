package com.infobip.kafkistry.service.produce.serialize

import com.infobip.kafkistry.model.TopicName

typealias SerializerType = String

interface ValueSerializer {
    val type: SerializerType
    fun serialize(topic: TopicName, value: String): ByteArray
}
