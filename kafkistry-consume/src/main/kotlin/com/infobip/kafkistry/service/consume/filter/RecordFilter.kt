package com.infobip.kafkistry.service.consume.filter

import com.infobip.kafkistry.service.consume.KafkaRecord
import com.infobip.kafkistry.service.consume.KafkaValue
import com.infobip.kafkistry.service.consume.ReadFilter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.function.Predicate

interface RecordFilter : (KafkaRecord) -> Boolean, Predicate<KafkaRecord> {
    override fun invoke(record: KafkaRecord): Boolean = test(record)
}

@Component
@ConditionalOnProperty("app.consume.enabled", matchIfMissing = true)
class RecordFilterFactory(
        private val matcherFactory: MatcherFactory
) {

    fun createFilter(readFilter: ReadFilter): RecordFilter {
        val headerFilter = readFilter.headerValueRule?.let {
            HeaderFilter(matcherFactory.createMatcher(it))
        }
        val jsonValueFilter = readFilter.jsonValueRule?.let {
            JsonFieldFilter(matcherFactory.createMatcher(it))
        }
        val keyValueFilter = readFilter.keyValueRule?.let {
            KeyFilter(matcherFactory.createMatcher(it))
        }
        val compositeFilter = createOrNull(
                all = readFilter.all?.map { createFilter(it) },
                any = readFilter.any?.map { createFilter(it) },
                none = readFilter.none?.map { createFilter(it) }
        )
        return allNonNull(headerFilter, jsonValueFilter, keyValueFilter, compositeFilter) ?: AcceptAllFilter.INSTANCE
    }

    private fun allNonNull(vararg filters: RecordFilter?): RecordFilter? {
        val allFilters = listOfNotNull(*filters)
        return when {
            allFilters.isEmpty() -> null
            allFilters.size == 1 -> allFilters[0]
            else -> CompositeRecordFilter(all = allFilters)
        }
    }

    private fun createOrNull(all: List<RecordFilter>?, any: List<RecordFilter>?, none: List<RecordFilter>?): RecordFilter? {
        return if (all != null || any != null || none != null) {
            CompositeRecordFilter(all, any, none)
        } else {
            null
        }
    }

}

class AcceptAllFilter : RecordFilter {

    companion object {
        val INSTANCE = AcceptAllFilter()
    }

    override fun test(record: KafkaRecord) = true

}

class JsonFieldFilter(private val matcher: Matcher) : RecordFilter {

    override fun test(record: KafkaRecord): Boolean {
        val recordValue = record.value.valueToFilterBy()
        return matcher.matches(true, recordValue)
    }

}

class HeaderFilter(private val matcher: Matcher) : RecordFilter {

    override fun test(record: KafkaRecord): Boolean {
        val headersMap = record.headers.associate {
            it.key to it.value.valueToFilterBy()
        }
        return matcher.matches(true, headersMap)
    }

}

class KeyFilter(private val matcher: Matcher) : RecordFilter {

    override fun test(record: KafkaRecord): Boolean {
        return matcher.matches(true, record.key.valueToFilterBy())
    }

}

private fun KafkaValue.valueToFilterBy(): Any? {
    if (isNull) return null
    return deserializations["JSON"]?.asFilterable
        ?: deserializations["STRING"]?.asFilterable
        ?: deserializations["LONG"]?.asFilterable
        ?: deserializations["INT"]?.asFilterable
        ?: deserializations.entries.firstOrNull()?.value?.asFilterable
}

class CompositeRecordFilter(
        private val all: List<RecordFilter>? = null,
        private val any: List<RecordFilter>? = null,
        private val none: List<RecordFilter>? = null
) : RecordFilter {

    override fun test(record: KafkaRecord): Boolean {
        val allOk = all?.all { it(record) } ?: true
        val anyOk = any?.any { it(record) } ?: true
        val noneOk = none?.none { it(record) } ?: true
        return allOk && anyOk && noneOk
    }

}

