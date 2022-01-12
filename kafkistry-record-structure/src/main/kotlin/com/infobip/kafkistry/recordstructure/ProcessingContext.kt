package com.infobip.kafkistry.recordstructure

open class ProcessingContext(
    val properties: RecordAnalyzerProperties,
    val now: Long = generateTimestamp(),
) {

    private val timeWindow: Long get() = properties.timeWindow

    fun <T> wrapNow(value: T) = TimestampWrapper(value, now)
    fun Long.tooOld() = this < now - timeWindow
}