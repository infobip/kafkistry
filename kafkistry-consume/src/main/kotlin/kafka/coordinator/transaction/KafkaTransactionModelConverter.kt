package kafka.coordinator.transaction

import com.infobip.kafkistry.kafka.toJavaList
import com.infobip.kafkistry.model.TransactionalId
import com.infobip.kafkistry.service.consume.interntopics.TransactionState
import com.infobip.kafkistry.service.consume.interntopics.TransactionState.TxnTopicPartition
import kafka.coordinator.group.KafkaGroupModelConverter
import kafka.coordinator.tryParseOrNull
import java.nio.ByteBuffer

object KafkaTransactionModelConverter {

    fun convert(txnKey: TxnKey): TransactionState.TxnKey {
        return TransactionState.TxnKey(
            version = txnKey.version(),
            transactionalId = txnKey.transactionalId(),
        )
    }

    fun tryParseTransactionMetadata(transactionalId: TransactionalId, byteBuffer: ByteBuffer): TransactionState? {
        return tryParseOrNull {
            TransactionLog.readTxnRecordValue(transactionalId, byteBuffer).getOrElse<TransactionMetadata> { null }
        }?.let(::convert)
    }

    private fun convert(txn: TransactionMetadata): TransactionState {
        return TransactionState(
            transactionalId = txn.transactionalId(),
            producerId = txn.producerId(),
            producerEpoch = txn.producerEpoch(),
            txnTimeoutMs = txn.txnTimeoutMs(),
            state = txn.state().toString(),
            topicPartitions = txn.topicPartitions().toJavaList().map { TxnTopicPartition(it.topic(), it.partition()) },
            txnStartTimestamp = txn.txnStartTimestamp(),
            txnLastUpdateTimestamp = txn.txnLastUpdateTimestamp(),
        )
    }

}