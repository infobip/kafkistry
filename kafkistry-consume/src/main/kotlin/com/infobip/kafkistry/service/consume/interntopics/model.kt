package com.infobip.kafkistry.service.consume.interntopics

data class ConsumerOffsetMetadata(
        val commit: ConsumerGroupOffsetCommit? = null,
        val metadata: ConsumerGroupMetadata? = null
) {

    data class ConsumerGroupRecordKey(
            val version: Short,
            val groupId: String?,
            val topic: String? = null,
            val partition: Int? = null
    )

    data class ConsumerGroupOffsetCommit(
            val offset: Long,
            val commitTimestamp: Long,
            val leaderEpoch: Int?,
            val expireTimestamp: Long?,
            val metadata: String?
    )

    data class ConsumerGroupMetadata(
            val groupId: String,
            val generationId: Int,
            val protocolType: String?,
            val currentState: String,
            val members: List<ConsumerGroupMetadataMember>
    )

    data class ConsumerGroupMetadataMember(
            val memberId: String,
            val groupInstanceId: String?,
            val clientId: String,
            val clientHost: String,
            val rebalanceTimeoutMs: Int,
            val sessionTimeoutMs: Int,
            val protocolType: String
    )

}

data class TransactionState(
        val transactionalId: String,
        var producerId: Long,
        var producerEpoch: Short,
        var txnTimeoutMs: Int,
        var state: String,
        val topicPartitions: List<TxnTopicPartition>,
        val txnStartTimestamp: Long,
        val txnLastUpdateTimestamp: Long
) {
    data class TxnKey(val version: Short, val transactionalId: String)
    data class TxnTopicPartition(val topic: String, val partition: Int)
}

