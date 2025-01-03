package com.infobip.kafkistry.service.resources

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import kotlin.math.abs

@Component
@ConfigurationProperties("app.resources.thresholds")
class UsageLevelThresholds {
    var low = 75.0
    var medium = 90.0
}

interface UsageLevelClassifier {

    fun determineLevel(percentage: Double?): UsageLevel

    companion object {
        val NONE = object : UsageLevelClassifier {
            override fun determineLevel(percentage: Double?): UsageLevel = UsageLevel.NONE
        }
    }

}

@Component
class DefaultUsageLevelClassifier(private val thresholds: UsageLevelThresholds) : UsageLevelClassifier {

    override fun determineLevel(percentage: Double?): UsageLevel {
        val absPercentage = percentage?.let { abs(it) }
        return with(thresholds) {
            when (absPercentage) {
                null -> UsageLevel.NONE
                in 0.0..low -> UsageLevel.LOW
                in low..medium -> UsageLevel.MEDIUM
                in medium..100.0 -> UsageLevel.HIGH
                else -> UsageLevel.OVERFLOW
            }
        }
    }
}