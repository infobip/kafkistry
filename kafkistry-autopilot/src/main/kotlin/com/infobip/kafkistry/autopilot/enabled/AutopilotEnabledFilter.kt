package com.infobip.kafkistry.autopilot.enabled

import com.infobip.kafkistry.autopilot.binding.AutopilotAction
import com.infobip.kafkistry.autopilot.binding.AutopilotBinding
import com.infobip.kafkistry.autopilot.enabled.AutopilotEnabledFilterProperties.StringValues
import com.infobip.kafkistry.utils.ClusterFilter
import com.infobip.kafkistry.utils.ClusterFilterProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.autopilot.actions")
class AutopilotEnabledFilterProperties {

    var enabled = false

    @NestedConfigurationProperty
    var bindingType = Classes<AutopilotBinding<*>>()

    @NestedConfigurationProperty
    var actionType = Classes<AutopilotAction>()

    @NestedConfigurationProperty
    var targetType = StringValues()

    @NestedConfigurationProperty
    var cluster = ClusterFilterProperties()

    var attribute: Map<String, StringValues> = emptyMap()

    class Classes<C> {
        var enabledClasses: Set<Class<out C>> = emptySet()
        var disabledClasses: Set<Class<out C>> = emptySet()
    }

    class StringValues {
        var enabledValues: Set<String> = emptySet()
        var disabledValues: Set<String> = emptySet()
    }
}

@Component
class AutopilotEnabledFilter(
    private val properties: AutopilotEnabledFilterProperties,
) {

    private val clusterFilter = ClusterFilter(properties.cluster)

    /**
     * Check if given [action] is **enabled** _(should execute)_ or **disabled** _(shouldn't execute)_.
     * Retuning `true` will prevent action execution.
     * @param binding to check if enabled/disabled
     * @param action to check if enabled/disabled
     * @return `true` - enabled; `false` - disabled
     */
    fun <A : AutopilotAction> isEnabled(
        binding: AutopilotBinding<A>, action: A,
    ): Boolean = with(properties) {
        return enabled &&
                bindingType.matchesType(binding) &&
                actionType.matchesType(action) &&
                targetType.matchesValue(action.metadata.description.targetType) &&
                action.metadata.clusterRef?.let(clusterFilter) != false &&
                attribute.matchesAttributes(action.metadata.attributes)
    }

    private fun <C : Any> AutopilotEnabledFilterProperties.Classes<C>.matchesType(instance: C): Boolean {
        val clazz = instance.javaClass
        return (enabledClasses.isEmpty() || enabledClasses.any { it.isAssignableFrom(clazz) }) &&
                (disabledClasses.isEmpty() || disabledClasses.none { it.isAssignableFrom(clazz) })
    }

    private fun StringValues.matchesValue(value: String): Boolean {
        return (enabledValues.isEmpty() || value in enabledValues) &&
                (disabledValues.isEmpty() || value !in disabledValues)
    }

    private fun Map<String, StringValues>.matchesAttributes(attributes: Map<String, String>): Boolean {
        return all { (attrKey, values) ->
            attributes[attrKey]?.let { values.matchesValue(it) } ?: true
        }
    }
}