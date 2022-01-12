package com.infobip.kafkistry.service.topic.offsets

class RateInstrument(
    maxBuckets: Int = 100
) {

    private val size = maxBuckets + 1
    private val offsets = Array(size) { 0L }
    private val times = Array(size) { 0L }
    private var index = 0

    fun observeNothing(timeMs: Long) {
        val lastOffset = offsets[(index - 1 + size) % size]
        observe(lastOffset, timeMs)
    }

    fun observe(offset: Long, timeMs: Long) {
        times[index % size] = timeMs
        offsets[index % size] = offset
        index++
    }

    fun rate(lastNumSamples: Int): Double? {
        require(lastNumSamples in 1 until size)
        if (lastNumSamples >= index) return null
        val offsetDiff = offsets.diffSinceLast(lastNumSamples)
        val timeDiff = times.diffSinceLast(lastNumSamples)
        return when {
            timeDiff > 0 -> offsetDiff.toDouble() / timeDiff.toDouble()
            else -> 0.0
        }
    }

    fun rate(): Double? = rate((index - 1).coerceIn(1, size - 1))

    private fun Array<Long>.diffSinceLast(lastNumSamples: Int): Long {
        val fromIndex = (index - lastNumSamples - 1 + size) % size
        val toIndex = (index - 1 + size) % size
        return this[toIndex] - this[fromIndex]
    }

}
