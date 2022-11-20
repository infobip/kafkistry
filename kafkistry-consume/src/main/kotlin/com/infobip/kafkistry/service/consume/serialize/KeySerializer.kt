package com.infobip.kafkistry.service.consume.serialize

import com.infobip.kafkistry.model.TopicName

typealias KeySerializerType = String

interface KeySerializer {

    val type: KeySerializerType
    fun serialize(topic: TopicName, key: String): ByteArray
}
