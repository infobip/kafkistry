package com.infobip.kafkistry.audit

import com.infobip.kafkistry.kafka.GroupOffsetResetChange
import com.infobip.kafkistry.kafka.GroupOffsetsReset
import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.service.acl.AclStatus
import com.infobip.kafkistry.service.acl.PrincipalAclsClusterInspection
import com.infobip.kafkistry.service.topic.TopicClusterStatus
import com.infobip.kafkistry.service.topic.TopicOnClusterInspectionResult
import com.infobip.kafkistry.service.quotas.QuotasInspection
import com.infobip.kafkistry.service.quotas.QuotasInspectionResultType
import com.infobip.kafkistry.webapp.security.User

abstract class AuditEvent {
    lateinit var serviceClass: String
    lateinit var user: User
    lateinit var methodName: String
    var encounteredException: String? = null
}

abstract class UpdateAuditEvent : AuditEvent() {
    var message: String? = null
}

abstract class ManagementAuditEvent : AuditEvent() {
    lateinit var clusterIdentifier: KafkaClusterIdentifier
}


class ClusterUpdateAuditEvent : UpdateAuditEvent() {
    lateinit var clusterIdentifier: KafkaClusterIdentifier
    var kafkaCluster: KafkaCluster? = null
}

fun ClusterUpdateAuditEvent.setCluster(cluster: KafkaCluster, msg: String) {
    clusterIdentifier = cluster.identifier
    kafkaCluster = cluster
    message = msg
}


class ClusterManagementAuditEvent : ManagementAuditEvent()


class TopicUpdateAuditEvent : UpdateAuditEvent() {
    lateinit var topicName: TopicName
    var topicDescription: TopicDescription? = null
}

fun TopicUpdateAuditEvent.setTopic(topic: TopicDescription, msg: String) {
    topicName = topic.name
    topicDescription = topic
    message = msg
}


class TopicManagementAuditEvent : ManagementAuditEvent() {
    lateinit var topicName: TopicName
    var kafkaTopicBefore: TopicOnClusterInspectionResult? = null
    var kafkaTopicAfter: TopicOnClusterInspectionResult? = null
}

fun TopicManagementAuditEvent.setKafkaTopicStatus(
    name: TopicName, cluster: KafkaClusterIdentifier,
    before: TopicClusterStatus?, after: TopicClusterStatus?
) {
    topicName = name
    clusterIdentifier = cluster
    kafkaTopicBefore = before?.status
    kafkaTopicAfter = after?.status
}


class ConsumerGroupManagementAuditEvent : ManagementAuditEvent() {
    lateinit var consumerGroupId: ConsumerGroupId
    var consumerGroupOffsetsReset: GroupOffsetsReset? = null
    var consumerGroupOffsetChange: GroupOffsetResetChange? = null
}

fun ConsumerGroupManagementAuditEvent.setConsumerGroup(
    clusterIdentifier: KafkaClusterIdentifier,
    consumerGroup: ConsumerGroupId
) {
    this.clusterIdentifier = clusterIdentifier
    consumerGroupId = consumerGroup
}


class PrincipalAclsUpdateAuditEvent : UpdateAuditEvent() {
    lateinit var principal: PrincipalId
    var principalAcls: PrincipalAclRules? = null
}

fun PrincipalAclsUpdateAuditEvent.setPrincipalAcls(principalRules: PrincipalAclRules, msg: String) {
    principal = principalRules.principal
    principalAcls = principalRules
    message = msg
}


class PrincipalAclsManagementAuditEvent : ManagementAuditEvent() {
    lateinit var principal: PrincipalId
    var aclRules: List<KafkaAclRule>? = null
    var principalStatusBefore: AclStatus? = null
    var principalStatusAfter: AclStatus? = null
}

fun PrincipalAclsManagementAuditEvent.setPrincipalAclsStatus(
    principalId: PrincipalId, cluster: KafkaClusterIdentifier,
    before: PrincipalAclsClusterInspection?, after: PrincipalAclsClusterInspection?
) {
    principal = principalId
    clusterIdentifier = cluster
    aclRules = (after ?: before)?.statuses?.map { it.rule }
    principalStatusBefore = before?.status
    principalStatusAfter = after?.status
}


class EntityQuotaUpdateAuditEvent : UpdateAuditEvent() {
    lateinit var quotaEntity: QuotaEntity
    var quotaDescription: QuotaDescription? = null
}

fun EntityQuotaUpdateAuditEvent.setQuotas(quota: QuotaDescription, msg: String) {
    quotaDescription = quota
    quotaEntity = quota.entity
    message = msg
}


class EntityQuotaManagementAuditEvent : ManagementAuditEvent() {
    lateinit var quotaEntity: QuotaEntity
    var quotaStatusBefore: QuotasInspectionResultType? = null
    var quotaBefore: QuotaProperties? = null
    var quotaStatusAfter: QuotasInspectionResultType? = null
    var quotaAfter: QuotaProperties? = null
}


fun EntityQuotaManagementAuditEvent.setQuotasStatus(
    entity: QuotaEntity, cluster: KafkaClusterIdentifier,
    before: QuotasInspection?, after: QuotasInspection?,
) {
    quotaEntity = entity
    clusterIdentifier = cluster
    quotaStatusBefore = before?.statusType
    quotaStatusAfter = after?.statusType
    quotaBefore = before?.actualQuota
    quotaAfter = after?.actualQuota
}


class TopicManualProduceAuditEvent : ManagementAuditEvent() {
    lateinit var topicName: TopicName
    var keyContent: String? = null
    var keySerializerType: String? = null
    var valueContent: String? = null
    var valueSerializerType: String? = null
    var headers: List<ProduceHeaderInfo> = emptyList()
    var partition: Int? = null
    var timestamp: Long? = null
    var produceSuccess: Boolean? = null
    var producePartition: Int? = null
    var produceOffset: Long? = null
    var produceTimestamp: Long? = null
    var errorMessage: String? = null
}

data class ProduceHeaderInfo(
    val key: String,
    val valueSerializerType: String?,  // null if header value is null
    val valueContent: String?,         // null if header value is null
)
