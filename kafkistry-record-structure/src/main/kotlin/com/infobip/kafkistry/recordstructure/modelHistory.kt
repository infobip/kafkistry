package com.infobip.kafkistry.recordstructure

import java.time.Duration

object TimeConstant {
    val offset15Min: Duration = Duration.ofMinutes(15)
    val offset1h: Duration = Duration.ofHours(1)
    val offset6h: Duration = Duration.ofHours(6)
    val offset1d: Duration = Duration.ofDays(1)
    val offset1w: Duration = Duration.ofDays(7)
    val offset1mo: Duration = Duration.ofDays(30)
}

data class TimedHistory<T>(
    val last: TimestampWrapper<T>,
    val last15Min: TimestampWrapper<T>,
    val lastHour: TimestampWrapper<T>,
    val last6Hours: TimestampWrapper<T>,
    val lastDay: TimestampWrapper<T>,
    val lastWeek: TimestampWrapper<T>,
    val lastMonth: TimestampWrapper<T>,
) {
    companion object {
        fun <T> of(last: TimestampWrapper<T>): TimedHistory<T> {
            return TimedHistory(last, last, last, last, last, last, last)
        }
    }
}

fun <T> TimedHistory<T>.maybeRollover(now: Long): TimedHistory<T> {
    fun TimestampWrapper<*>.needRoll(maxOffset: Duration): Boolean {
        return now > timestamp + maxOffset.toMillis()
    }
    var rolled = false
    fun TimestampWrapper<T>.maybeRoll(
        maxOffset: Duration,
        lowerBucket: TimestampWrapper<T>,
    ): TimestampWrapper<T> {
        return if (rolled || needRoll(maxOffset)) {
            rolled = true
            lowerBucket
        } else {
            this
        }
    }
    // statement order is important because of [rolled]
    // if we roll some bucket, we need to roll all lower level bucket
    val newLastMonth = lastMonth.maybeRoll(TimeConstant.offset1mo, lastWeek)
    val newLastWeek = lastWeek.maybeRoll(TimeConstant.offset1w, lastDay)
    val newLastDay = lastDay.maybeRoll(TimeConstant.offset1d, last6Hours)
    val newLast6Hours = last6Hours.maybeRoll(TimeConstant.offset6h, lastHour)
    val newLastHour = lastHour.maybeRoll(TimeConstant.offset1h, last15Min)
    val newLast15Min = last15Min.maybeRoll(TimeConstant.offset15Min, last)
    return TimedHistory(last, newLast15Min, newLastHour, newLast6Hours, newLastDay, newLastWeek, newLastMonth)
}

inline fun <T> TimedHistory<T>.merge(
    other: TimedHistory<T>,
    merge: (self: TimestampWrapper<T>, other: TimestampWrapper<T>) -> TimestampWrapper<T>,
): TimedHistory<T> {
    return TimedHistory(
        last = other.last,
        last15Min = merge(last15Min, other.last15Min),
        lastHour = merge(lastHour, other.lastHour),
        last6Hours = merge(last6Hours, other.last6Hours),
        lastDay = merge(lastDay, other.lastDay),
        lastWeek = merge(lastWeek, other.lastWeek),
        lastMonth = merge(lastMonth, other.lastMonth),
    )
}
