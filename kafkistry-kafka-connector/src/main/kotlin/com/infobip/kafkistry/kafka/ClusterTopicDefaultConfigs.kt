package com.infobip.kafkistry.kafka

import org.apache.kafka.clients.admin.ConfigEntry

object ClusterTopicDefaultConfigs {

    private val EMPTY_DEFAULT = ConfigValue(
        "", default = true, readOnly = false, sensitive = false, source = ConfigEntry.ConfigSource.DEFAULT_CONFIG
    )

    fun extractClusterExpectedValue(clusterConfig: ExistingConfig, nameKey: String): ConfigValue? {
        fun tryGet(key: String): ConfigValue? = if (key.endsWith(".ms")) {
            clusterConfig.timedPropertyLookup(key.removeSuffix(".ms"))
        } else {
            clusterConfig[key]
        }
        return nothing()
            .orElse {
                //handle all exceptionally named cluster server properties
                when (nameKey) {
                    "delete.retention.ms" -> tryGet("log.cleaner.delete.retention.ms")
                    "file.delete.delay.ms" -> tryGet("log.segment.delete.delay.ms")
                    "flush.messages" -> tryGet("log.flush.interval.messages")
                    "flush.ms" -> nothing()
                        .orElse { tryGet("log.flush.interval.ms") }
                        .orElse { tryGet("log.flush.scheduler.interval.ms") }
                    "max.compaction.lag.ms" -> tryGet("log.cleaner.max.compaction.lag.ms")
                    "min.compaction.lag.ms" -> tryGet("log.cleaner.min.compaction.lag.ms")
                    "max.message.bytes" -> tryGet("message.max.bytes")
                    "min.cleanable.dirty.ratio" -> tryGet("log.cleaner.min.cleanable.ratio")
                    "message.format.version" -> tryGet("log.message.format.version")
                    "segment.index.bytes" -> tryGet("log.index.size.max.bytes")
                    "segment.jitter.ms" -> tryGet("log.roll.jitter.ms")
                    "segment.ms" -> tryGet("log.roll.ms")
                    "remote.storage.enable" -> tryGet("remote.log.storage.system.enable")
                    "follower.replication.throttled.replicas" -> EMPTY_DEFAULT
                    "leader.replication.throttled.replicas" -> EMPTY_DEFAULT
                    else -> null
                }
            }
            .orElse { tryGet("log.$nameKey") }
            .orElse { tryGet(nameKey) }
    }

    private fun ExistingConfig.timedPropertyLookup(prefix: String): ConfigValue? = nothing()
        .orElse { this["${prefix}.ms"] }
        .orElse { this["${prefix}.minutes"]?.let { it.copy(value = it.value?.toLong()?.times(1000 * 60)?.toString()) } }
        .orElse { this["${prefix}.hours"]?.let { it.copy(value = it.value?.toLong()?.times(1000 * 3600)?.toString()) } }

    private fun nothing(): ConfigValue? = null

    private fun ConfigValue?.orElse(supplier: () -> ConfigValue?): ConfigValue? {
        return this ?: supplier()?.takeIf { it.value != null }
    }

}
