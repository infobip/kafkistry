package com.infobip.kafkistry.service.resources

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.resources.thresholds")
class UsageLevelThresholds {
    var low = 65.0
    var medium = 90.0
}

@Component
class UsageLevelClassifier(private val thresholds: UsageLevelThresholds) {

    fun determineLevel(percentage: Double?): UsageLevel {
        return with(thresholds) {
            when (percentage) {
                null -> UsageLevel.NONE
                in 0.0..low -> UsageLevel.LOW
                in low..medium -> UsageLevel.MEDIUM
                in medium..100.0 -> UsageLevel.HIGH
                else -> UsageLevel.OVERFLOW
            }
        }
    }
}