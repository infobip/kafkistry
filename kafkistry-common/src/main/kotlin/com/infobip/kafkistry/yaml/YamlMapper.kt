package com.infobip.kafkistry.yaml

import com.infobip.kafkistry.model.TopicDescription
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.dataformat.yaml.YAMLFactory
import tools.jackson.dataformat.yaml.YAMLFactoryBuilder
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.dataformat.yaml.YAMLWriteFeature
import tools.jackson.module.kotlin.KotlinModule

@Component
class YamlMapper {

    private val mapper: ObjectMapper = YAMLFactoryBuilder(YAMLFactory())
        .enable(YAMLWriteFeature.LITERAL_BLOCK_STYLE)
        .build()
        .let {
            YAMLMapper.builder(it)
                .addModule(KotlinModule.Builder().build())
                .build()
        }

    fun serialize(any: Any?): String = mapper.writeValueAsString(any)

    fun <T> deserialize(yaml: String, clazz: Class<T>): T = mapper.readValue(yaml, clazz)

}

fun main() {
    val yaml = """
        ---
        name: "ES.message-events"
        owner: "Team_IPCore, Team_Kafka, Team_Compass"
        description: "PoC for ingesting data into Azure Event Stream"
        labels:
        - category: "Product"
          name: "CPaaS - CPaaS Shared Platform - CPaaS Shared Platform"
          externalId: "12"
        resourceRequirements: null
        producer: "infobip-darkgreen-to-kafka"
        presence:
          type: "INCLUDED_CLUSTERS"
          kafkaClusterIdentifiers:
          - "kafka-iop1"
          - "kafka-iot1"
          tag: null
        properties:
          partitionCount: 24
          replicationFactor: 3
        config:
          retention.bytes: "524288000"
          segment.bytes: "52428800"
          retention.ms: "172800000"
          max.message.bytes: "5242880"
        perClusterProperties: {}
        perClusterConfigOverrides: {}
        perTagProperties: {}
        perTagConfigOverrides: {}
        freezeDirectives: []
        fieldDescriptions:
        - selector: "OriginalSenderName"
          classifications:
          - "PII"
          description: ""
        - selector: "ManipulatedSenderName"
          classifications:
          - "PII"
          description: ""
        - selector: "SenderAddress"
          classifications:
          - "PII"
          description: ""
        - selector: "DestinationAddress"
          classifications:
          - "PII"
          description: ""
        allowManualProduce: null
        """.trimIndent()
    val mapper = YamlMapper()
    val desc = mapper.deserialize(yaml, TopicDescription::class.java)
    println(desc)
}