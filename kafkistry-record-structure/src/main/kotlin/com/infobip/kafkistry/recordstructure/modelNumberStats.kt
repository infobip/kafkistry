package com.infobip.kafkistry.recordstructure

import kotlin.math.max
import kotlin.math.min

data class IntNumberSummary(
    val sum: Long,
    val count: Int,
    val min: Int,
    val max: Int,
) {
    val avg: Int get() = (sum / count).toInt()

    companion object {
        fun ofSingle(number: Int) = IntNumberSummary(sum = number.toLong(), count = 1, min = number, max = number)
    }
}

infix fun IntNumberSummary.merge(other: IntNumberSummary) = IntNumberSummary(
    sum = sum + other.sum,
    count = count + other.count,
    max = max(max, other.max),
    min = min(min, other.min),
)