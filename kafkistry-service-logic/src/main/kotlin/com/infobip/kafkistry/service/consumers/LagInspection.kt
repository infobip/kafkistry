package com.infobip.kafkistry.service.consumers

import com.infobip.kafkistry.kafka.PartitionOffsets
import com.infobip.kafkistry.service.consumers.config.LagInspectProperties
import org.springframework.stereotype.Component
import java.util.Comparator
import java.util.concurrent.TimeUnit

@Component
class LagInspection(
        private val lagProperties: LagInspectProperties
) {

    fun inspectLag(
            partitionOffset: PartitionOffsets?,
            memberOffset: Long?,
            partitionMsgRate: Double?,
            memberAssigned: Boolean,
    ): Lag {
        fun computeLag(): Pair<Long?, Double?> {
            if (partitionOffset == null) {
                return null to null  // no offsets of partition -> don't know anything
            }
            val partitionSize = partitionOffset.end - partitionOffset.begin
            if (memberOffset == null) {
                // consumer never committed -> it's either no lag or unknown
                return if (partitionSize == 0L) 0L to 0.0 else null to null
            }
            val lag = (partitionOffset.end - memberOffset).coerceAtLeast(0)
            val lagPercentage = if (partitionSize == 0L) {
                if (lag == 0L) 0.0 else Double.POSITIVE_INFINITY
            } else {
                100.0 * lag.toDouble() / partitionSize.toDouble()
            }
            return lag to lagPercentage
        }
        val (lagAmount, lagPercentage) = computeLag()
        return Lag(
            amount = lagAmount,
            percentage = lagPercentage,
            status = lagStatus(lagAmount, lagPercentage, partitionMsgRate, memberAssigned)
        )
    }

    private fun lagStatus(lag: Long?, percentage: Double?, partitionMsgRate: Double?, memberAssigned: Boolean): LagStatus {
        if (lag == null || percentage == null) {
            return LagStatus.UNKNOWN
        }
        if (percentage > 100.0) {
            val oneRecordIn15MinRate = 1.0 / TimeUnit.MINUTES.toSeconds(15)
            val lowRateStagnation = memberAssigned && lag < lagProperties.partitionTxnLagTolerateAmount &&
                    partitionMsgRate != null && partitionMsgRate < oneRecordIn15MinRate
            if (!lowRateStagnation) {
                return LagStatus.OVERFLOW
            }
        }
        val amountLagStatus = when (lag) {
            in lagProperties.noLagAmountRange() -> LagStatus.NO_LAG
            in lagProperties.minorAmountLagRange() -> LagStatus.MINOR_LAG
            else -> LagStatus.HAS_LAG
        }
        if (partitionMsgRate == null || partitionMsgRate < 1) {
            return amountLagStatus
        }
        val lagTime = lag / partitionMsgRate
        val timeLagStatus = when (lagTime.toLong()) {
            in lagProperties.noLagTimeRange() -> LagStatus.NO_LAG
            in lagProperties.minorTimeLagRange() -> LagStatus.MINOR_LAG
            else -> LagStatus.HAS_LAG
        }
        return maxOf(amountLagStatus, timeLagStatus, Comparator.comparing { it.level })
    }

    fun aggregate(lags: List<Lag>): Lag {
        if (lags.isEmpty()) {
            return Lag(null, null, LagStatus.UNKNOWN)
        }
        val totalAmount = lags.mapNotNull { it.amount }.takeIf { it.isNotEmpty() }?.sum()
        val maxPercentage = lags.asSequence().mapNotNull { it.percentage }.maxOrNull()
        val totalStatus = lags.asSequence().map { it.status }.maxByOrNull { it.level } ?: LagStatus.UNKNOWN
        return Lag(totalAmount, maxPercentage, totalStatus)
    }

}

data class Lag(
    val amount: Long?,
    val percentage: Double?,
    val status: LagStatus,
)
