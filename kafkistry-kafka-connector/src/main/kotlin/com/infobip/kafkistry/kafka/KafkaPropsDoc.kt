package com.infobip.kafkistry.kafka

import kafka.server.KafkaConfig
import org.apache.kafka.common.config.TopicConfig as AdminClientTopicConfig

private val A_TAG_REGEX = """<a href=".*?">(.*?)</a>""".toRegex()

val TOPIC_CONFIG_DOC = AdminClientTopicConfig::class.java
    .declaredFields
    .let { fields ->
        val properties = fields
            .filter { it.name.endsWith("_CONFIG") }
            .associate { it.name.removeSuffix("_CONFIG") to it.get(null) as String }
        val docs = fields
            .filter { it.name.endsWith("_DOC") }
            .associate { it.name.removeSuffix("_DOC") to it.get(null) as String }
            .mapValues { (_, doc) ->
                doc.replace(A_TAG_REGEX) { "<i>"+it.groupValues[1]+"</i>" }
            }
        properties.map { it.value to docs[it.key].orEmpty() }.toMap()
    }

val TOPIC_CONFIG_PROPERTIES = TOPIC_CONFIG_DOC.keys.sorted()

val BROKER_CONFIG_DOC = Class.forName(KafkaConfig::class.java.name+"$")
    .declaredFields
    .let { fields ->
        fields.forEach { it.trySetAccessible() }
        val properties = fields
            .filter { it.name.endsWith("Prop") }
            .associate { it.name.removeSuffix("Prop") to it.get(null) as String }
        val docs = fields
            .filter { it.name.endsWith("Doc") }
            .associate { it.name.removeSuffix("Doc") to it.get(null) as String }
            .mapValues { (_, doc) ->
                doc.replace(A_TAG_REGEX) { "<i>"+it.groupValues[1]+"</i>" }
            }
        properties.map { it.value to docs[it.key].orEmpty() }.toMap()
    }

