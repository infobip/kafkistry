package com.infobip.kafkistry.service.consume.interntopics

import com.infobip.kafkistry.shaded.kafka.coordinator.group.GroupMetadataKey
import com.infobip.kafkistry.shaded.kafka.coordinator.group.GroupMetadataManager
import com.infobip.kafkistry.shaded.kafka.coordinator.group.KafkaGroupModelConverter
import com.infobip.kafkistry.shaded.kafka.coordinator.group.OffsetKey
import com.infobip.kafkistry.shaded.kafka.coordinator.transaction.KafkaTransactionModelConverter
import com.infobip.kafkistry.shaded.kafka.coordinator.transaction.TransactionLog
import com.infobip.kafkistry.shaded.kafka.coordinator.transaction.TxnKey
import com.infobip.kafkistry.shaded.kafka.coordinator.tryParseOrNull
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.stereotype.Component
import java.nio.ByteBuffer

@Component
class InternalTopicsValueReader {

    fun ConsumerRecord<ByteArray, ByteArray>.consumerOffsetKey(): ConsumerOffsetMetadata.ConsumerGroupRecordKey? {
        val baseKey = key()?.let {
            tryParseOrNull {
                GroupMetadataManager.readMessageKey(ByteBuffer.wrap(it))
            }
        } ?: return null
        return when (baseKey) {
            is OffsetKey -> KafkaGroupModelConverter.convert(baseKey)
            is GroupMetadataKey -> KafkaGroupModelConverter.convert(baseKey)
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
                }?.let { KafkaGroupModelConverter.convert(it) }
            )
            else -> ConsumerOffsetMetadata(
                metadata = KafkaGroupModelConverter.tryParseGroupMetadata(key.groupId, byteBuffer)
            )
        }
    }

    fun ConsumerRecord<ByteArray, ByteArray>.transactionStateKey(): TransactionState.TxnKey? {
        val byteBuffer = key()?.let { ByteBuffer.wrap(it) } ?: return null
        return tryParseOrNull { TransactionLog.readTxnRecordKey(byteBuffer) }
            ?.let {
                if (it is TxnKey) {
                    KafkaTransactionModelConverter.convert(it)
                } else null
            }
    }

    fun ConsumerRecord<ByteArray, ByteArray>.transactionStateValue(): TransactionState? {
        val byteBuffer = value().let { ByteBuffer.wrap(it) } ?: return null
        val txnKey = transactionStateKey() ?: return null
        return KafkaTransactionModelConverter.tryParseTransactionMetadata(txnKey.transactionalId, byteBuffer)
    }

}