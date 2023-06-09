package com.infobip.kafkistry.service.topic

import com.infobip.kafkistry.kafka.ClusterTopicDefaultConfigs
import com.infobip.kafkistry.kafka.ConfigValue
import com.infobip.kafkistry.kafka.ExistingConfig
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
            val expectingClusterDefault = isValueSameAsExpectedByCluster(nameKey, expectedValue, clusterConfig)
            inspectRegistryExpectedValue(expectedValue, actualValue, expectingClusterDefault)
        }
    }

    private fun inspectRegistryExpectedValue(
        expectedValue: String,
        actualValue: ConfigValue,
        expectingClusterDefault: Boolean,
    ): ValueInspection {
        //since value is defined in registry explicitly, actual value need to be same as it
        return if (expectedValue == actualValue.value) {
            ValueInspection(true, actualValue.value, expectedValue, expectingClusterDefault)
        } else {
            ValueInspection(false, actualValue.value, expectedValue, expectingClusterDefault)
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
                    "message.format.version" -> inspectMessageFormatVersion(actualValue, clusterExpectedValue)
                    else -> ValueInspection(false, actualValue.value, clusterExpectedValue.value, true)
                }
            }
        }
    }

    private fun ExistingConfig.extractClusterExpectedValue(nameKey: String): ConfigValue? {
        return ClusterTopicDefaultConfigs.extractClusterExpectedValue(this, nameKey)
    }

    private fun inspectMessageFormatVersion(
        actualValue: ConfigValue, clusterExpectedValue: ConfigValue,
    ): ValueInspection {
        fun String?.matchesMessageFormatVersion(expected: String?): Boolean {
            if (expected == null) return false
            return this?.startsWith(expected) ?: false
        }
        return if (actualValue.value.matchesMessageFormatVersion(clusterExpectedValue.value)) {
            ValueInspection(true, actualValue.value, actualValue.value, true)
        } else {
            ValueInspection(false, actualValue.value, clusterExpectedValue.value, true)
        }
    }

}
