package com.infobip.kafkistry.service.consume.filter

import com.infobip.kafkistry.service.consume.FieldRuleType.*
import com.infobip.kafkistry.service.consume.ValueRule
import org.springframework.stereotype.Component

interface Matcher {

    fun matches(exists: Boolean, value: Any?): Boolean
}

@Component
class MatcherFactory(private val jsonPathParser: JsonPathParser) {

    fun createMatcher(valueRule: ValueRule): Matcher {
        val valueMatcher = ValueMatcher(valueRule)
        if (valueRule.name.isEmpty()) {
            return valueMatcher
        }
        val pathElements = jsonPathParser.parseJsonKeyPath(valueRule.name)
        var matcher: Matcher = valueMatcher
        for (pathElement in pathElements.reversed()) {
            matcher = when (pathElement) {
                is ListIndexPathElement -> ListMatcher(pathElement, matcher)
                is MapKeyPathElement -> MapMatcher(pathElement, matcher)
            }
        }
        return matcher
    }

}

abstract class CollectionMatcher(private val subMatcher: Matcher) : Matcher {

    protected fun matchAny(values: Collection<*>): Boolean {
        for (fieldValue in values) {
            if (subMatcher.matches(true, fieldValue)) {
                return true
            }
        }
        if (values.isEmpty()) {
            return subMatcher.matches(false, null)
        }
        return false
    }
}

class MapMatcher(private val mapKey: MapKeyPathElement, private val subMatcher: Matcher) : CollectionMatcher(subMatcher) {

    override fun matches(exists: Boolean, value: Any?): Boolean {
        if (value !is Map<*, *>) {
            return subMatcher.matches(false, null)
        }
        if (mapKey.keyName != null) {
            val fieldValue = value[mapKey.keyName]
            val keyExist = mapKey.keyName in value.keys
            return subMatcher.matches(keyExist, fieldValue)
        }
        return matchAny(value.values)
    }
}

class ListMatcher(private val listIndex: ListIndexPathElement, private val subMatcher: Matcher) : CollectionMatcher(subMatcher) {

    override fun matches(exists: Boolean, value: Any?): Boolean {
        if (value !is List<*>) {
            return subMatcher.matches(false, null)
        }
        if (listIndex.index != null) {
            val indexExist = listIndex.index in value.indices
            val fieldValue = if (indexExist) value[listIndex.index] else null
            return subMatcher.matches(indexExist, fieldValue)
        }
        return matchAny(value)
    }
}


class ValueMatcher(private val valueRule: ValueRule) : Matcher {

    private val longValue: Long? = valueRule.value.toLongOrNull()
    private val intValue: Int? = valueRule.value.toIntOrNull()
    private val doubleValue: Double? = valueRule.value.toDoubleOrNull()
    private val regexValue: Regex? = if (valueRule.type in setOf(REGEX, NOT_REGEX)) valueRule.value.toRegex() else null

    override fun matches(exists: Boolean, value: Any?): Boolean {
        return when (valueRule.type) {
            EXIST -> exists
            NOT_EXIST -> !exists
            IS_NULL -> exists && value == null
            NOT_NULL -> exists && value != null
            EQUAL_TO -> value?.let { valueRule.value == it.toString() } ?: false
            NOT_EQUAL_TO -> value?.let { valueRule.value != it.toString() } ?: true
            LESS_THAN -> value?.let { lessThan(it) } ?: false
            GREATER_THAN -> value?.let { greaterThan(it) } ?: false
            CONTAINS -> value?.let { contains(it) } ?: false
            NOT_CONTAINS -> value?.let { notContains(it) } ?: true
            REGEX -> value?.let { regexValue?.containsMatchIn(it.toString()) } ?: false
            NOT_REGEX -> value?.let { regexValue?.containsMatchIn(it.toString())?.not() } ?: true
        }
    }

    private fun lessThan(fieldValue: Any): Boolean {
        return when (fieldValue) {
            is Int -> intValue?.let { fieldValue < it } ?: false
            is Long -> longValue?.let { fieldValue < it } ?: false
            is Double -> doubleValue?.let { fieldValue < it } ?: false
            is String -> fieldValue < valueRule.value
            is List<*>  -> intValue?.let { fieldValue.size < it } ?: false
            is Map<*, *> -> intValue?.let { fieldValue.size < it } ?: false
            else -> false
        }
    }

    private fun greaterThan(fieldValue: Any): Boolean {
        return when (fieldValue) {
            is Int -> intValue?.let { fieldValue > it } ?: false
            is Long -> longValue?.let { fieldValue > it } ?: false
            is Double -> doubleValue?.let { fieldValue > it } ?: false
            is String -> fieldValue > valueRule.value
            is List<*>  -> intValue?.let { fieldValue.size > it } ?: false
            is Map<*, *> -> intValue?.let { fieldValue.size > it } ?: false
            else -> false
        }
    }

    private fun contains(fieldValue: Any): Boolean {
        return when (fieldValue) {
            is String -> valueRule.value in fieldValue
            is List<*> -> valueRule.value in fieldValue.map { it.toString() }
            else -> false
        }
    }

    private fun notContains(fieldValue: Any): Boolean {
        return when (fieldValue) {
            is String -> valueRule.value !in fieldValue
            is List<*> -> valueRule.value !in fieldValue.map { it.toString() }
            else -> false
        }
    }

}
