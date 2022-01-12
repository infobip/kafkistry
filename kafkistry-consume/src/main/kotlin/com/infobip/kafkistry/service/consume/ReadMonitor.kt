package com.infobip.kafkistry.service.consume

import org.apache.kafka.clients.consumer.ConsumerRecord
import com.infobip.kafkistry.kafka.Partition
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Class which will observe reading progress.
 */
interface ReadMonitor {

    companion object {
        fun ofConfig(readConfig: ReadConfig): ReadMonitor = ReadMonitorImpl(readConfig.maxWaitMs, readConfig.waitStrategy)
    }

    /**
     * Inform this monitor that new record is received
     */
    fun receivedRecord(record: ConsumerRecord<ByteArray, ByteArray>)

    /**
     * Inform this monitor that record passed filtration
     */
    fun gotFilteredRecord(record: KafkaRecord)

    /**
     * Invocation will make sure that reading is withing timeout bounds, and if desired waitStrategy is fulfilled
     * @return true if more records need to be fetched from kafka; false otherwise
     */
    fun needToReadMore(): Boolean

    /**
     * Was reading progress topped because of timeout
     */
    fun hasTimedOut(): Boolean

    /**
     * How many records is received from kafka
     */
    fun totalRecords(): Int

    fun partitionsReadStatus(): Map<Partition, PartitionStats>

    data class PartitionStats(
        var first: Long,
        var last: Long = first,
        var matching: Int = 0,
    )
}

class ReadMonitorImpl(
        private val maxWaitTimeMs: Long,
        private val waitStrategy: WaitStrategy
) : ReadMonitor {

    private val startTime = System.currentTimeMillis()
    private val totalRecords = AtomicInteger(0)
    private val filteredRecords = AtomicInteger(0)
    private val hasTimedOut = AtomicBoolean(false)
    private val partitionsOffsets = ConcurrentHashMap<Partition, ReadMonitor.PartitionStats>(100)

    override fun receivedRecord(record: ConsumerRecord<ByteArray, ByteArray>) {
        totalRecords.incrementAndGet()
        val offsets = partitionOffsets(record.partition(), record.offset())
        offsets.last = record.offset()
    }

    override fun gotFilteredRecord(record: KafkaRecord) {
        filteredRecords.incrementAndGet()
        partitionOffsets(record.partition, record.offset).matching++
    }

    override fun needToReadMore(): Boolean {
        if (timeExceeded()) {
            hasTimedOut.set(true)
            return false
        }
        return when (waitStrategy) {
            WaitStrategy.AT_LEAST_ONE -> filteredRecords.get() == 0
            WaitStrategy.WAIT_NUM_RECORDS -> true
        }
    }

    override fun hasTimedOut() = hasTimedOut.get()
    override fun totalRecords() = totalRecords.get()

    override fun partitionsReadStatus(): Map<Partition, ReadMonitor.PartitionStats> = partitionsOffsets.toMap()

    private fun partitionOffsets(partition: Partition, offset: Long) =
        partitionsOffsets.computeIfAbsent(partition) {
            ReadMonitor.PartitionStats(offset)
        }

    private fun timeExceeded()  = (System.currentTimeMillis() - startTime) > maxWaitTimeMs
}