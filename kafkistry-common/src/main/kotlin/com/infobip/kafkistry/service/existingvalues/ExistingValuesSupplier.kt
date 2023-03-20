package com.infobip.kafkistry.service.existingvalues

import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.KafkaUser
import com.infobip.kafkistry.model.Label
import com.infobip.kafkistry.model.TopicName

interface ExistingValuesSupplier {

    fun topics(): List<TopicName> = emptyList()
    fun consumerGroups(): List<ConsumerGroupId> = emptyList()
    fun owners(): List<String> = emptyList()
    fun producers(): List<String> = emptyList()
    fun users(): List<KafkaUser> = emptyList()
    fun labels(): List<Label> = emptyList()

    fun <T> getOrEmpty(supplier: () -> List<T>): List<T> = try {
        supplier()
    } catch (_: Exception) {
        emptyList()
    }

}
