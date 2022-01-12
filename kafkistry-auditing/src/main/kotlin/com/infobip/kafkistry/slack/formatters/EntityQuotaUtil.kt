package com.infobip.kafkistry.slack.formatters

import com.infobip.kafkistry.model.QuotaEntity
import com.infobip.kafkistry.model.QuotaProperties

fun QuotaEntity.format(): String {
    return with(StringBuilder()) {
        if (!userIsAny()) {
            append("`User` ")
            append(if (userIsDefault()) "_(default)_" else user)
        }
        if (!userIsAny() && !clientIsAny()) {
            append(" | ")
        }
        if (!clientIsAny()) {
            append("`ClientId` ")
            append(if (clientIsDefault()) "_(default)_" else clientId)
        }
        toString()
    }
}

fun QuotaProperties.format(): String {
    return listOfNotNull(
        producerByteRate?.let { "Produce rate: `$it` bytes/sec" },
        consumerByteRate?.let { "Consume rate: `$it` bytes/sec" },
        requestPercentage?.let { "Request percentage: `$it` %" },
    ).joinToString(separator = "\n")
}