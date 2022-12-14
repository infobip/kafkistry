@file:Suppress("JpaDataSourceORMInspection")

package com.infobip.kafkistry.sql.sources

import com.infobip.kafkistry.kafka.ConsumerGroupStatus
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.consumers.ConsumersService
import com.infobip.kafkistry.service.consumers.KafkaConsumerGroup
import com.infobip.kafkistry.service.consumers.LagStatus
import com.infobip.kafkistry.sql.SqlDataSource
import org.springframework.stereotype.Component
import java.io.Serializable
import jakarta.persistence.*

@Component
class ConsumerGroupsDataSource(
    private val consumersService: ConsumersService,
) : SqlDataSource<Group> {

    override fun modelAnnotatedClass(): Class<Group> = Group::class.java

    override fun supplyEntities(): List<Group> {
        val allConsumersData = consumersService.allConsumersData()
        return allConsumersData.clustersGroups.map { clusterGroups ->
            mapConsumerGroup(clusterGroups.clusterIdentifier, clusterGroups.consumerGroup)
        }
    }

    private fun mapConsumerGroup(
        clusterIdentifier: KafkaClusterIdentifier,
        consumerGroup: KafkaConsumerGroup
    ): Group {
        return Group().apply {
            id = ClusterConsumerGroupId().apply {
                cluster = clusterIdentifier
                groupId = consumerGroup.groupId
            }
            status = consumerGroup.status
            assignor = consumerGroup.partitionAssignor
            totalLag = consumerGroup.lag.amount
            assignments = consumerGroup.topicMembers.flatMap { topicMembers ->
                topicMembers.partitionMembers.map { member ->
                    ConsumerGroupAssignment().apply {
                        cluster = clusterIdentifier
                        topic = topicMembers.topicName
                        partition = member.partition
                        lag = member.lag.amount
                        offset = member.offset
                        lagPercentage = member.lag.percentage
                        status = member.lag.status
                        memberId = member.member?.memberId
                        clientId = member.member?.clientId
                        memberHost = member.member?.host
                    }
                }
            }
        }
    }

}

@Embeddable
class ClusterConsumerGroupId : Serializable {

    lateinit var cluster: KafkaClusterIdentifier
    lateinit var groupId: ConsumerGroupId
}


@Entity
@Table(name = "Groups")
class Group {

    @EmbeddedId
    lateinit var id: ClusterConsumerGroupId

    @Enumerated(EnumType.STRING)
    lateinit var status: ConsumerGroupStatus

    lateinit var assignor: String

    var totalLag: Long? = null

    @ElementCollection
    @JoinTable(name = "Groups_Assignments")
    lateinit var assignments: List<ConsumerGroupAssignment>
}

@Embeddable
class ConsumerGroupAssignment {

    lateinit var cluster: KafkaClusterIdentifier
    lateinit var topic: TopicName

    @Column(nullable = false)
    var partition: Int? = null

    var lag: Long? = null
    var offset: Long? = null
    var lagPercentage: Double? = null
    @Enumerated(EnumType.STRING)
    lateinit var status: LagStatus

    var memberId: String? = null
    var clientId: String? = null
    var memberHost: String? = null
}

