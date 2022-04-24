package com.infobip.kafkistry.service.topic

import org.apache.kafka.clients.admin.ConfigEntry
import com.infobip.kafkistry.kafka.ConfigValue
import com.infobip.kafkistry.kafka.ExistingConfig
import com.infobip.kafkistry.service.ConfigValueChange
import com.infobip.kafkistry.service.ValueInspection
import org.springframework.stereotype.Component

@Component
class ConfigValueInspector {

    fun checkConfigProperty(
            nameKey: String,
            actualValue: ConfigValue,
            expectedValue: String?,
            clusterConfig: ExistingConfig
    ): ValueInspection = inspect(nameKey, actualValue, expectedValue, clusterConfig)

    fun needIncludeConfigValue(
            nameKey: String,
            actualValue: ConfigValue,
            clusterConfig: ExistingConfig
    ): Boolean = inspect(nameKey, actualValue, null, clusterConfig).valid.not()

    fun requiredConfigValueChange(
            nameKey: String,
            actualValue: ConfigValue,
            expectedValue: String?,
            clusterConfig: ExistingConfig
    ): ConfigValueChange? = inspect(nameKey, actualValue, expectedValue, clusterConfig)
            .takeIf { it.currentValue != it.expectedValue }
            ?.let { ConfigValueChange(nameKey, it.currentValue, it.expectedValue, it.expectingClusterDefault) }

    fun isValueSameAsExpectedByCluster(
            nameKey: String, value: String?, clusterConfig: ExistingConfig
    ): Boolean = clusterConfig.extractClusterExpectedValue(nameKey)?.value == value

    fun clusterDefaultValue(clusterConfig: ExistingConfig, nameKey: String): ConfigValue? {
        return clusterConfig.extractClusterExpectedValue(nameKey)
    }

    private fun inspect(
            nameKey: String,
            actualValue: ConfigValue,
            expectedValue: String?,
            clusterConfig: ExistingConfig
    ): ValueInspection {
        return if (expectedValue == null) {
            inspectRegistryUndefinedValue(clusterConfig, nameKey, actualValue)
        } else {
            inspectRegistryExpectedValue(expectedValue, actualValue)
        }
    }

    private fun inspectRegistryExpectedValue(expectedValue: String, actualValue: ConfigValue): ValueInspection {
        //since value is defined in registry explicitly, actual value need to be same as it
        return if (expectedValue == actualValue.value) {
            ValueInspection(true, actualValue.value, expectedValue, false)
        } else {
            ValueInspection(false, actualValue.value, expectedValue, false)
        }
    }

    private fun inspectRegistryUndefinedValue(clusterConfig: ExistingConfig, nameKey: String, actualValue: ConfigValue): ValueInspection {
        val clusterExpectedValue = clusterConfig.extractClusterExpectedValue(nameKey)
        return if (clusterExpectedValue == null) {
            if (actualValue.default) {
                //value is not specified in registry's config and value on cluster is default, so ok then
                ValueInspection(true, actualValue.value, actualValue.value, true)
            } else {
                ValueInspection(false, actualValue.value, null, true)
            }
        } else {
            if (clusterExpectedValue.value == actualValue.value) {
                ValueInspection(true, actualValue.value, clusterExpectedValue.value, true)
            } else {
                when (nameKey) {
                    "message.format.version" -> {
                        if (actualValue.value.matchesMessageFormatVersion(clusterExpectedValue.value)) {
                            ValueInspection(true, actualValue.value, actualValue.value, true)
                        } else {
                            ValueInspection(false, actualValue.value, clusterExpectedValue.value, true)
                        }
                    }
                    else -> ValueInspection(false, actualValue.value, clusterExpectedValue.value, true)
                }
            }
        }
    }

    private fun ExistingConfig.extractClusterExpectedValue(nameKey: String): ConfigValue? {
        val clusterConfig = this
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

    private fun String?.matchesMessageFormatVersion(expected: String?): Boolean {
        if (expected == null) return false
        return this?.startsWith(expected) ?: false
    }

}