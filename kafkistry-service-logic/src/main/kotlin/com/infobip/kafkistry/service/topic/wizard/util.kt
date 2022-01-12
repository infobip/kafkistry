package com.infobip.kafkistry.service.topic.wizard

import kotlin.math.roundToInt
import kotlin.math.roundToLong

fun Long.toPrettySize(): String {
    if (this < 5000) {
        return "$this bytes"
    }
    val kb = this / 1024
    if (kb < 5000) {
        return "$kb kB"
    }
    val mb = kb / 1024
    if (mb < 5000) {
        return "$mb MB"
    }
    val gb = mb / 1024
    if (gb < 5000) {
        return "$gb GB"
    }
    val tb = gb / 1024
    if (tb < 5000) {
        return "$tb TB"
    }
    val yb = tb / 1024
    return "$yb YB"
}

fun Double.toPrettyRate(unit: String): String {
    if (this >= 1) {
        if (this < 1000) {
            return "${this.round2Dec()} $unit/sec"
        }
        val rateK = this / 1000
        if (rateK < 1000) {
            return "${rateK.round2Dec()} k$unit/sec"
        }
        val rateM = rateK / 1000
        if (rateM < 1000) {
            return "${rateK.round2Dec()} M$unit/sec"
        }
        val rateG = rateM / 1000
        return "${rateG.round2Dec()} G$unit/sec"
    } else {
        val periodSec = 1.0 / this
        if (periodSec < 60) {
            return "$unit every ${periodSec.round2Dec()} sec"
        }
        val periodMin = periodSec / 60
        if (periodMin < 60) {
            return "$unit every ${periodMin.round2Dec()} min"
        }
        val periodH = periodMin / 60
        return "$unit every ${periodH.round2Dec()} hours"
    }
}

fun Double.round2Dec(): Double = (this * 100).roundToLong() / 100.0
fun Double.ceil(): Int = kotlin.math.ceil(this).roundToInt()

