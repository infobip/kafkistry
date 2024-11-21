package com.infobip.kafkistry.service.existingvalues

import com.infobip.kafkistry.model.*

interface ExistingValuesSupplier {

    fun topics(): List<TopicName> = emptyList()
    fun consumerGroups(): List<ConsumerGroupId> = emptyList()
    fun owners(): List<String> = emptyList()
    fun producers(): List<String> = emptyList()
    fun users(): List<KafkaUser> = emptyList()
    fun labels(): List<Label> = emptyList()
    fun fieldClassifications(): List<FieldClassification> = emptyList()

    fun <T> getOrEmpty(supplier: () -> List<T>): List<T> = try {
        supplier()
    } catch (_: Exception) {
        emptyList()
    }

}
