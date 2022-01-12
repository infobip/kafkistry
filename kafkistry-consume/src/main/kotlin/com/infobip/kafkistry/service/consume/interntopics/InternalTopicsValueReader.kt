package com.infobip.kafkistry.service.consume.interntopics

import kafka.coordinator.group.GroupMetadataKey
import kafka.coordinator.group.GroupMetadataManager
import kafka.coordinator.group.OffsetKey
import kafka.coordinator.transaction.TransactionLog
import kafka.coordinator.transaction.TransactionMetadata
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.utils.SystemTime
import org.springframework.stereotype.Component
import java.nio.ByteBuffer

@Component
class InternalTopicsValueReader {

    private val time = SystemTime()

    fun ConsumerRecord<ByteArray, ByteArray>.consumerOffsetKey(): ConsumerOffsetMetadata.ConsumerGroupRecordKey? {
        val baseKey = key()?.let {
            tryParseOrNull {
                GroupMetadataManager.readMessageKey(ByteBuffer.wrap(it))
            }
        } ?: return null
        return when (baseKey) {
            is OffsetKey -> KafkaModelConverter.convert(baseKey)
            is GroupMetadataKey -> KafkaModelConverter.convert(baseKey)
            else -> null
        }
    }

    fun ConsumerRecord<ByteArray, ByteArray>.consumerOffsetValue(): ConsumerOffsetMetadata? {
        val byteBuffer = value().let { ByteBuffer.wrap(it) } ?: return null
        val key = consumerOffsetKey() ?: return null
        return when {
            key.topic != null && key.partition != null -> ConsumerOffsetMetadata(
                commit = tryParseOrNull {
                    GroupMetadataManager.readOffsetMessageValue(byteBuffer)
                }?.let { KafkaModelConverter.convert(it) }
            )
            else -> ConsumerOffsetMetadata(
                metadata = tryParseOrNull {
                    GroupMetadataManager.readGroupMessageValue(key.groupId, byteBuffer, time)
                }?.let { KafkaModelConverter.convert(it) }
            )
        }
    }

    fun ConsumerRecord<ByteArray, ByteArray>.transactionStateKey(): TransactionState.TxnKey? {
        val byteBuffer = key()?.let { ByteBuffer.wrap(it) } ?: return null
        return tryParseOrNull { TransactionLog.readTxnRecordKey(byteBuffer) }?.let(KafkaModelConverter::convert)
    }

    fun ConsumerRecord<ByteArray, ByteArray>.transactionStateValue(): TransactionState? {
        val byteBuffer = value().let { ByteBuffer.wrap(it) } ?: return null
        val txnKey = transactionStateKey() ?: return null
        return tryParseOrNull {
            TransactionLog.readTxnRecordValue(txnKey.transactionalId, byteBuffer).getOrElse<TransactionMetadata> { null }
        }?.let(KafkaModelConverter::convert)
    }

    private fun <T> tryParseOrNull(operation: () -> T): T? = try {
        operation()
    } catch (_: IllegalStateException) {
        null
    }

}