package com.infobip.kafkistry.kafka

import org.apache.kafka.clients.admin.ConfigEntry

object ClusterTopicDefaultConfigs {

    fun extractClusterExpectedValue(clusterConfig: ExistingConfig, nameKey: String): ConfigValue? {
        return nothing<ConfigValue>()
            .orElse {
                //handle all exceptionally named cluster server properties
                when (nameKey) {
                    "delete.retention.ms" -> clusterConfig["log.cleaner.delete.retention.ms"]
                    "file.delete.delay.ms" -> clusterConfig["log.segment.delete.delay.ms"]
                    "flush.messages" -> clusterConfig["log.flush.interval.messages"]
                    "flush.ms" -> clusterConfig["log.flush.interval.ms"]
                    "max.compaction.lag.ms" -> clusterConfig["log.cleaner.max.compaction.lag.ms"]
                    "min.compaction.lag.ms" -> clusterConfig["log.cleaner.min.compaction.lag.ms"]
                    "max.message.bytes" -> clusterConfig["message.max.bytes"]
                    "min.cleanable.dirty.ratio" -> clusterConfig["log.cleaner.min.cleanable.ratio"]
                    "message.format.version" -> clusterConfig["log.message.format.version"]
                    "segment.index.bytes" -> clusterConfig["log.index.size.max.bytes"]
                    "segment.jitter.ms" -> clusterConfig["log.roll.jitter.ms"]
                    "segment.ms" -> clusterConfig["log.roll.ms"]
                    "follower.replication.throttled.replicas" -> ConfigValue("", default = true, readOnly = false, sensitive = false, source = ConfigEntry.ConfigSource.DEFAULT_CONFIG)
                    "leader.replication.throttled.replicas" -> ConfigValue("", default = true, readOnly = false, sensitive = false, source = ConfigEntry.ConfigSource.DEFAULT_CONFIG)
                    else -> null
                }
            }
            .orElse { clusterConfig["log.$nameKey"] }
            .orElse { clusterConfig[nameKey] }
            ?.takeIf { it.value != null }
    }

    private fun <T> nothing(): T? = null

    private fun <T> T?.orElse(supplier: () -> T?): T? {
        return this ?: supplier()
    }

}
