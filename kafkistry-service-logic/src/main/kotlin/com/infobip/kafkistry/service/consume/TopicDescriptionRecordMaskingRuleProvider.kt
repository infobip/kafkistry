package com.infobip.kafkistry.service.consume

import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.FieldClassification
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.ExistingValues
import com.infobip.kafkistry.service.consume.masking.RecordMaskingRuleProvider
import com.infobip.kafkistry.service.consume.masking.TopicMaskingSpec
import com.infobip.kafkistry.service.existingvalues.CLASSIFICATION_PII
import com.infobip.kafkistry.service.existingvalues.CLASSIFICATION_SECRET
import com.infobip.kafkistry.service.existingvalues.ExistingValuesSupplier
import com.infobip.kafkistry.service.topic.TopicsRegistryService
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "app.masking.topic-description-fields")
class TopicDescriptionRecordMaskingRuleProviderProperties {
    var enabled = true
    var classificationsToMask: List<FieldClassification> = listOf(
        CLASSIFICATION_PII, CLASSIFICATION_SECRET,
    )
}

@Component
class TopicDescriptionRecordMaskingRuleProvider(
    private val properties: TopicDescriptionRecordMaskingRuleProviderProperties,
    private val topicsRegistryService: TopicsRegistryService,
) : RecordMaskingRuleProvider, ExistingValuesSupplier {

    override fun maskingSpecFor(topic: TopicName, clusterRef: ClusterRef): List<TopicMaskingSpec> {
        if (!properties.enabled) {
            return emptyList()
        }
        val fieldDescriptions = topicsRegistryService.findTopic(topic)
            ?.fieldDescriptions.orEmpty()
        val maskingFieldSelectors = fieldDescriptions
            .filter { field ->
                field.classifications.any { it in properties.classificationsToMask }
            }
            .map { it.selector }
            .toSet()
        return listOf(
            TopicMaskingSpec(
                keyPathDefs = emptySet(),
                headerPathDefs = emptyMap(),
                valuePathDefs = maskingFieldSelectors,
            )
        )
    }

    override fun fieldClassifications(): List<FieldClassification> = properties.classificationsToMask
}