package com.infobip.kafkistry.metric

import com.infobip.kafkistry.kafka.KafkaPrincipal
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.consumers.ConsumerTopicPartitionMember
import com.infobip.kafkistry.service.ownership.ConsumerGroupToPrincipalResolver
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

interface LagMetricsAdditionalLabels {

    fun labelNames(): List<String>

    fun labelValues(
        clusterIdentifier: KafkaClusterIdentifier,
        topic: TopicName,
        consumerGroupId: ConsumerGroupId,
        partitionMember: ConsumerTopicPartitionMember,
    ): List<String>
}

class EmptyLagMetricsAdditionalLabels : LagMetricsAdditionalLabels {

    override fun labelNames(): List<String> = emptyList()

    override fun labelValues(
        clusterIdentifier: KafkaClusterIdentifier,
        topic: TopicName,
        consumerGroupId: ConsumerGroupId,
        partitionMember: ConsumerTopicPartitionMember,
    ): List<String> = emptyList()

}

@Component
@ConfigurationProperties("app.metrics.consumer-lag.additional-labels")
class KafkaLagServiceAdditionalLabelProviderConfig {
    var enabled = false
    var serviceLabelName = "service"
    var ownerLabelName = "owners"
    var unknownServiceLiteralValue = "unknown"
    var unknownOwnerLiteralValue = "unknown"
}

@Component
@ConditionalOnProperty("app.metrics.consumer-lag.additional-labels.enabled", matchIfMissing = false)
class KafkaLagServiceAdditionalLabelProvider(
    props: KafkaLagServiceAdditionalLabelProviderConfig,
    private val consumerGroupPrincipalResolver: ConsumerGroupToPrincipalResolver,
) : LagMetricsAdditionalLabels {

    private val labels = listOf(props.serviceLabelName, props.ownerLabelName)
    private val unknown = listOf(props.unknownServiceLiteralValue, props.unknownOwnerLiteralValue)

    override fun labelNames(): List<String> = labels

    override fun labelValues(
        clusterIdentifier: KafkaClusterIdentifier,
        topic: TopicName,
        consumerGroupId: ConsumerGroupId,
        partitionMember: ConsumerTopicPartitionMember,
    ): List<String> {
        val principalsOwners = consumerGroupPrincipalResolver.resolvePrincipalOwner(consumerGroupId)
        return if (principalsOwners == null) {
            unknown
        } else {
            val serviceNames = principalsOwners.principalsIds.joinToString(",") { KafkaPrincipal.usernameOf(it) }
            val adGroups = principalsOwners.owners.takeIf { it.isNotEmpty() }?.joinToString(",")
            listOf(serviceNames, adGroups ?: "unknown")
        }
    }

}