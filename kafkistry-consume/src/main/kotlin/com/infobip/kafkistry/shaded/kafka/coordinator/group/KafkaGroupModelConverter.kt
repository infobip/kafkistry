package com.infobip.kafkistry.shaded.kafka.coordinator.group

import com.infobip.kafkistry.kafka.toJavaList
import com.infobip.kafkistry.service.consume.interntopics.ConsumerOffsetMetadata
import com.infobip.kafkistry.shaded.kafka.common.OffsetAndMetadata
import com.infobip.kafkistry.shaded.kafka.coordinator.tryParseOrNull
import com.infobip.kafkistry.shaded.org.apache.kafka.common.utils.Time
import java.nio.ByteBuffer

object KafkaGroupModelConverter {

    private val time = Time.SYSTEM

    fun convert(key: OffsetKey): ConsumerOffsetMetadata.ConsumerGroupRecordKey {
        return ConsumerOffsetMetadata.ConsumerGroupRecordKey(
            version = key.version(),
            groupId = key.key().group(),
            topic = key.key().topicPartition().topic(),
            partition = key.key().topicPartition().partition(),
        )
    }

    fun convert(key: GroupMetadataKey): ConsumerOffsetMetadata.ConsumerGroupRecordKey {
        return ConsumerOffsetMetadata.ConsumerGroupRecordKey(
            version = key.version(),
            groupId = key.key(),
            topic = null,
            partition = null,
        )
    }

    fun convert(metadata: OffsetAndMetadata): ConsumerOffsetMetadata.ConsumerGroupOffsetCommit {
        return ConsumerOffsetMetadata.ConsumerGroupOffsetCommit(
            offset = metadata.offset(),
            commitTimestamp = metadata.commitTimestamp(),
            leaderEpoch = metadata.leaderEpoch().orElse(null),
            expireTimestamp = metadata.expireTimestamp().getOrElse<Long?> { null },
            metadata = metadata.metadata(),
        )
    }

    fun tryParseGroupMetadata(groupId: String?, value: ByteBuffer): ConsumerOffsetMetadata.ConsumerGroupMetadata? {
        return tryParseOrNull {
            GroupMetadataManager.readGroupMessageValue(groupId, value, time)
        }?.let { convert(it) }
    }

    private fun convert(metadata: GroupMetadata): ConsumerOffsetMetadata.ConsumerGroupMetadata {
        return ConsumerOffsetMetadata.ConsumerGroupMetadata(
            groupId = metadata.groupId(),
            generationId = metadata.generationId(),
            protocolType = metadata.protocolType().getOrElse<String?> { null },
            currentState = metadata.currentState().toString(),
            members = metadata.allMemberMetadata().toJavaList().map { convert(it) },
        )
    }

    private fun convert(member: MemberMetadata): ConsumerOffsetMetadata.ConsumerGroupMetadataMember {
        return ConsumerOffsetMetadata.ConsumerGroupMetadataMember(
            memberId = member.memberId(),
            groupInstanceId = member.groupInstanceId().getOrElse<String?> { null },
            clientId = member.clientId(),
            clientHost = member.clientHost(),
            rebalanceTimeoutMs = member.rebalanceTimeoutMs(),
            sessionTimeoutMs = member.sessionTimeoutMs(),
            protocolType = member.protocolType(),
        )
    }

}