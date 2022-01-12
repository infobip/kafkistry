package com.infobip.kafkistry.service.consume.interntopics

import kafka.common.OffsetAndMetadata
import kafka.coordinator.group.GroupMetadata
import kafka.coordinator.group.GroupMetadataKey
import kafka.coordinator.group.MemberMetadata
import kafka.coordinator.group.OffsetKey
import kafka.coordinator.transaction.TransactionMetadata
import kafka.coordinator.transaction.TxnKey
import com.infobip.kafkistry.service.consume.interntopics.ConsumerOffsetMetadata.*
import com.infobip.kafkistry.service.consume.interntopics.TransactionState.TxnTopicPartition
import com.infobip.kafkistry.kafka.toJavaList

object KafkaModelConverter {

    fun convert(key: OffsetKey): ConsumerGroupRecordKey {
        return ConsumerGroupRecordKey(
            version = key.version(),
            groupId = key.key().group(),
            topic = key.key().topicPartition().topic(),
            partition = key.key().topicPartition().partition(),
        )
    }

    fun convert(key: GroupMetadataKey): ConsumerGroupRecordKey {
        return ConsumerGroupRecordKey(
            version = key.version(),
            groupId = key.key(),
            topic = null,
            partition = null,
        )
    }

    fun convert(metadata: OffsetAndMetadata): ConsumerGroupOffsetCommit {
        return ConsumerGroupOffsetCommit(
            offset = metadata.offset(),
            commitTimestamp = metadata.commitTimestamp(),
            leaderEpoch = metadata.leaderEpoch().orElse(null),
            expireTimestamp = metadata.expireTimestamp().getOrElse<Long?> { null },
            metadata = metadata.metadata(),
        )
    }

    fun convert(metadata: GroupMetadata): ConsumerGroupMetadata {
        return ConsumerGroupMetadata(
            groupId = metadata.groupId(),
            generationId = metadata.generationId(),
            protocolType = metadata.protocolType().getOrElse<String?> { null },
            currentState = metadata.currentState().toString(),
            members = metadata.allMemberMetadata().toJavaList().map { convert(it) },
        )
    }

    private fun convert(member: MemberMetadata): ConsumerGroupMetadataMember {
        return ConsumerGroupMetadataMember(
            memberId = member.memberId(),
            groupInstanceId = member.groupInstanceId().getOrElse<String?> { null },
            clientId = member.clientId(),
            clientHost = member.clientHost(),
            rebalanceTimeoutMs = member.rebalanceTimeoutMs(),
            sessionTimeoutMs = member.sessionTimeoutMs(),
            protocolType = member.protocolType(),
        )
    }

    fun convert(txnKey: TxnKey): TransactionState.TxnKey {
        return TransactionState.TxnKey(
            version = txnKey.version(),
            transactionalId = txnKey.transactionalId(),
        )
    }

    fun convert(txn: TransactionMetadata): TransactionState {
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