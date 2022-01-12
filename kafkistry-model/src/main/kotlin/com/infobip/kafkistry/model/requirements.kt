package com.infobip.kafkistry.model

import java.io.Serializable
import java.util.concurrent.TimeUnit

data class ResourceRequirements(
    val avgMessageSize: MsgSize,
    val retention: DataRetention,
    val retentionOverrides: Map<KafkaClusterIdentifier, DataRetention> = emptyMap(),
    val retentionTagOverrides: Map<Tag, DataRetention> = emptyMap(),
    val messagesRate: MessagesRate,
    val messagesRateOverrides: Map<KafkaClusterIdentifier, MessagesRate> = emptyMap(),
    val messagesRateTagOverrides: Map<Tag, MessagesRate> = emptyMap(),
) : Serializable

data class MsgSize(
    val amount: Int,
    val unit: BytesUnit,
) : Serializable

data class DataRetention(
    val amount: Int,
    val unit: TimeUnit,
) : Serializable

data class MessagesRate(
    val amount: Long,
    val factor: ScaleFactor,
    val unit: RateUnit,
) : Serializable

enum class BytesUnit(val factor: Int) {
    B(1),
    KB(1 shl 10),
    MB(1 shl 20),
    GB(1 shl 30),
}

enum class ScaleFactor(val value: Int) {
    ONE(1),
    K(1_000),
    M(1_000_000),
    G(1_000_000_000),
}

enum class RateUnit(val millis: Long) {
    MSG_PER_DAY(1000L * 60 * 60 * 24),
    MSG_PER_HOUR(1000L * 60 * 60),
    MSG_PER_MINUTE(1000L * 60),
    MSG_PER_SECOND(1000L),
}

fun ResourceRequirements.messagesRate(clusterRef: ClusterRef): MessagesRate =
    messagesRateOverrides[clusterRef.identifier]
        ?: clusterRef.tags.mapNotNull { messagesRateTagOverrides[it] }.firstOrNull()
        ?: messagesRate

fun ResourceRequirements.retention(clusterRef: ClusterRef): DataRetention =
    retentionOverrides[clusterRef.identifier]
        ?: clusterRef.tags.mapNotNull { retentionTagOverrides[it] }.firstOrNull()
        ?: retention

fun MsgSize.toBytes(): Long = amount.toLong() * unit.factor

fun DataRetention.toMillis(): Long = unit.toMillis(amount.toLong())

fun MessagesRate.ratePerDay(): Double = ratePerMs() * TimeUnit.DAYS.toMillis(1)
fun MessagesRate.ratePerSec(): Double = ratePerMs() * TimeUnit.SECONDS.toMillis(1)
fun MessagesRate.ratePerMs(): Double = amount.toDouble() * factor.value / unit.millis
