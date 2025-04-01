package kafka.coordinator.group

import com.infobip.kafkistry.kafka.toJavaList
import com.infobip.kafkistry.service.consume.interntopics.ConsumerOffsetMetadata.*
import kafka.common.OffsetAndMetadata
import kafka.coordinator.tryParseOrNull
import org.apache.kafka.common.utils.Time
import java.nio.ByteBuffer

object KafkaGroupModelConverter {

    private val time = Time.SYSTEM

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

    fun tryParseGroupMetadata(groupId: String?, value: ByteBuffer): ConsumerGroupMetadata? {
        return tryParseOrNull {
            GroupMetadataManager.readGroupMessageValue(groupId, value, time)
        }?.let { convert(it) }
    }

    private fun convert(metadata: GroupMetadata): ConsumerGroupMetadata {
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

}