package com.infobip.kafkistry.sql

import com.infobip.kafkistry.kafka.KafkaAclRule
import com.infobip.kafkistry.service.kafkastreams.KStreamAppId
import com.infobip.kafkistry.model.ConsumerGroupId
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.PrincipalId
import com.infobip.kafkistry.model.QuotaEntityID
import com.infobip.kafkistry.model.TopicName

////////////////////////////////
// Result of query execution
////////////////////////////////

data class QueryResult(
        val count: Int,
        val totalCount: Int,
        val columns: List<ColumnMeta>,
        val columnLinkedType: Map<Int, LinkedResource.Type>,
        val linkedCompoundTypes: List<LinkedResource.Type>,
        val rows: List<QueryResultRow>
)

data class ColumnMeta(
        val name: String,
        val type: String
)

data class QueryResultRow(
        val values: List<Any?>,
        val linkedResource: LinkedResource?
)

data class LinkedResource(
    val types: List<Type>,
    val topic: TopicName? = null,
    val cluster: KafkaClusterIdentifier? = null,
    val group: ConsumerGroupId? = null,
    val principal: PrincipalId? = null,
    val acl: KafkaAclRule? = null,
    val quotaEntityID: QuotaEntityID? = null,
    val kafkaStreamAppId: KStreamAppId? = null,
) {
    enum class Type {
        TOPIC,
        CLUSTER_TOPIC,
        CLUSTER,
        CLUSTER_GROUP,
        PRINCIPAL,
        PRINCIPAL_ACL,
        PRINCIPAL_CLUSTER,
        QUOTA_ENTITY,
        KSTREAM_APP,
    }
}


////////////////////////////////
// Utility
////////////////////////////////

data class TableInfo(
        val name: String,
        val joinTable: Boolean,
        val columns: List<ColumnInfo>
)

data class ColumnInfo(
        val name: String,
        val type: String,
        val primaryKey: Boolean,
        val joinKey: Boolean,
        val referenceKey: Boolean
)

data class QueryExample(
        val title: String,
        val sql: String
)

data class TableStats(
    val name: String,
    val count: Int,
)
