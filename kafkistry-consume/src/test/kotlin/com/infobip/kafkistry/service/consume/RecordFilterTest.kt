package com.infobip.kafkistry.service.consume

import com.infobip.kafkistry.model.ClusterRef
import io.kotlintest.matchers.shouldBe
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import com.infobip.kafkistry.service.consume.FieldRuleType.*
import com.infobip.kafkistry.service.consume.filter.JsonPathParser
import com.infobip.kafkistry.service.consume.filter.MatcherFactory
import com.infobip.kafkistry.service.consume.filter.RecordFilterFactory
import org.junit.Test
import java.util.*

class RecordFilterTest {

    private val filterFactory = RecordFilterFactory(MatcherFactory(JsonPathParser()))
    private val recordFactory = RecordFactoryTest.factory

    @Test
    fun `empty filter matches all`() {
        val filter = filterFactory.createFilter(ReadFilter.EMPTY)
        filter(record()) shouldBe true
        filter(record(value = "abcdf5dcan===")) shouldBe true
        filter(record(value = """{"key":"val"}""")) shouldBe true
    }

    @Test
    fun `field exist`() {
        val filter = valueFilter("foo", EXIST).create()
        filter(record(value = "jcvewtvu6c")) shouldBe false
        filter(record(value = """{"key":123}""")) shouldBe false
        filter(record(value = """{"foo":123}""")) shouldBe true
        filter(record(value = """{"x":true,"foo":123}""")) shouldBe true
    }

    @Test
    fun `field not exist`() {
        val filter = valueFilter("foo", NOT_EXIST).create()
        filter(record(value = "jcvewtvu6c")) shouldBe true
        filter(record(value = """{"key":123}""")) shouldBe true
        filter(record(value = """{"foo":123}""")) shouldBe false
        filter(record(value = """{"x":true,"foo":123}""")) shouldBe false
    }

    @Test
    fun `field is null`() {
        val filter = valueFilter("foo", IS_NULL).create()
        filter(record(value = "jcvewtvu6c")) shouldBe false
        filter(record(value = """{"key":123}""")) shouldBe false
        filter(record(value = """{"foo":123}""")) shouldBe false
        filter(record(value = """{"x":true,"foo":null}""")) shouldBe true
    }

    @Test
    fun `field not null`() {
        val filter = valueFilter("foo", NOT_NULL).create()
        filter(record(value = "jcvewtvu6c")) shouldBe false
        filter(record(value = """{"key":123}""")) shouldBe false
        filter(record(value = """{"foo":123}""")) shouldBe true
        filter(record(value = """{"x":true,"foo":null}""")) shouldBe false
    }

    @Test
    fun `field equal to string`() {
        val filter = valueFilter("foo", EQUAL_TO, "abc").create()
        filter(record(value = "jcvewtvu6c")) shouldBe false
        filter(record(value = """{"key":"abc"}""")) shouldBe false
        filter(record(value = """{"foo":"abc"}""")) shouldBe true
        filter(record(value = """{"foo":null}""")) shouldBe false
        filter(record(value = """{"foo":"xyz"}""")) shouldBe false
    }

    @Test
    fun `field not equal to string`() {
        val filter = valueFilter("foo", NOT_EQUAL_TO, "abc").create()
        filter(record(value = "jcvewtvu6c")) shouldBe true
        filter(record(value = """{"key":"abc"}""")) shouldBe true
        filter(record(value = """{"foo":"abc"}""")) shouldBe false
        filter(record(value = """{"foo":null}""")) shouldBe true
        filter(record(value = """{"foo":"xyz"}""")) shouldBe true
    }

    @Test
    fun `field equal to number`() {
        val filter = valueFilter("foo", EQUAL_TO, "123").create()
        filter(record(value = "jcvewtvu6c")) shouldBe false
        filter(record(value = """{"key":123}""")) shouldBe false
        filter(record(value = """{"foo":123}""")) shouldBe true
        filter(record(value = """{"foo":null}""")) shouldBe false
        filter(record(value = """{"foo":456}""")) shouldBe false
    }

    @Test
    fun `field not equal to number`() {
        val filter = valueFilter("foo", NOT_EQUAL_TO, "123").create()
        filter(record(value = "jcvewtvu6c")) shouldBe true
        filter(record(value = """{"key":123}""")) shouldBe true
        filter(record(value = """{"foo":123}""")) shouldBe false
        filter(record(value = """{"foo":null}""")) shouldBe true
        filter(record(value = """{"foo":456}""")) shouldBe true
    }

    @Test
    fun `field less than number`() {
        val filter = valueFilter("foo", LESS_THAN, "123").create()
        filter(record(value = "jcvewtvu6c")) shouldBe false
        filter(record(value = """{"key":55}""")) shouldBe false
        filter(record(value = """{"foo":55}""")) shouldBe true
        filter(record(value = """{"foo":null}""")) shouldBe false
        filter(record(value = """{"foo":666}""")) shouldBe false
    }

    @Test
    fun `field less than string`() {
        val filter = valueFilter("foo", LESS_THAN, "ccc").create()
        filter(record(value = "jcvewtvu6c")) shouldBe false
        filter(record(value = """{"key":"aaa"}""")) shouldBe false
        filter(record(value = """{"foo":"aaa"}""")) shouldBe true
        filter(record(value = """{"foo":null}""")) shouldBe false
        filter(record(value = """{"foo":"ddd"}""")) shouldBe false
    }

    @Test
    fun `field greater than number`() {
        val filter = valueFilter("foo", GREATER_THAN, "123").create()
        filter(record(value = "jcvewtvu6c")) shouldBe false
        filter(record(value = """{"key":666}""")) shouldBe false
        filter(record(value = """{"foo":55}""")) shouldBe false
        filter(record(value = """{"foo":null}""")) shouldBe false
        filter(record(value = """{"foo":666}""")) shouldBe true
    }

    @Test
    fun `field greater than string`() {
        val filter = valueFilter("foo", GREATER_THAN, "ccc").create()
        filter(record(value = "jcvewtvu6c")) shouldBe false
        filter(record(value = """{"key":"ddd"}""")) shouldBe false
        filter(record(value = """{"foo":"aaa"}""")) shouldBe false
        filter(record(value = """{"foo":null}""")) shouldBe false
        filter(record(value = """{"foo":"ddd"}""")) shouldBe true
    }

    @Test
    fun `field contains string`() {
        val filter = valueFilter("foo", CONTAINS, "yyy").create()
        filter(record(value = "jcvewtvu6c")) shouldBe false
        filter(record(value = """{"key":"xxx yyy zzz"}""")) shouldBe false
        filter(record(value = """{"foo":"xxx yyy zzz"}""")) shouldBe true
        filter(record(value = """{"foo":null}""")) shouldBe false
        filter(record(value = """{"foo":"aaa bbb ccc"}""")) shouldBe false
    }

    @Test
    fun `field contains number`() {
        val filter = valueFilter("foo", CONTAINS, "222").create()
        filter(record(value = "jcvewtvu6c")) shouldBe false
        filter(record(value = """{"key":111222333}""")) shouldBe false
        filter(record(value = """{"foo":111222333}""")) shouldBe false
        filter(record(value = """{"foo":null}""")) shouldBe false
        filter(record(value = """{"foo":444555666}""")) shouldBe false
    }

    @Test
    fun `field contains array number`() {
        val filter = valueFilter("foo", CONTAINS, "222").create()
        filter(record(value = "jcvewtvu6c")) shouldBe false
        filter(record(value = """{"key":[111,222,333]}""")) shouldBe false
        filter(record(value = """{"foo":[111,222,333]}""")) shouldBe true
        filter(record(value = """{"foo":null}""")) shouldBe false
        filter(record(value = """{"foo":[444,555,666]}""")) shouldBe false
    }

    @Test
    fun `field contains array string`() {
        val filter = valueFilter("foo", CONTAINS, "bbb").create()
        filter(record(value = "jcvewtvu6c")) shouldBe false
        filter(record(value = """{"key":["aaa","bbb","ccc"]}""")) shouldBe false
        filter(record(value = """{"foo":["aaa","bbb","ccc"]}""")) shouldBe true
        filter(record(value = """{"foo":null}""")) shouldBe false
        filter(record(value = """{"foo":["xxx","yyy","zzz"]}""")) shouldBe false
    }

    @Test
    fun `field contains array map`() {
        val filter = valueFilter("foo", CONTAINS, "{x=1, y=2}").create()
        filter(record(value = "jcvewtvu6c")) shouldBe false
        filter(record(value = """{"key":[{"x":1,"y":2},{"x":3,"y":4}]}""")) shouldBe false
        filter(record(value = """{"foo":[{"x":1,"y":2},{"x":3,"y":4}]}""")) shouldBe true
        filter(record(value = """{"foo":null}""")) shouldBe false
        filter(record(value = """{"foo":[{"x":5,"y":6},{"x":7,"y":8}]}""")) shouldBe false
    }

    @Test
    fun `field not contains string`() {
        val filter = valueFilter("foo", NOT_CONTAINS, "yyy").create()
        filter(record(value = "jcvewtvu6c")) shouldBe true
        filter(record(value = """{"key":"xxx yyy zzz"}""")) shouldBe true
        filter(record(value = """{"foo":"xxx yyy zzz"}""")) shouldBe false
        filter(record(value = """{"foo":null}""")) shouldBe true
        filter(record(value = """{"foo":"aaa bbb ccc"}""")) shouldBe true
    }

    @Test
    fun `field not contains number`() {
        val filter = valueFilter("foo", NOT_CONTAINS, "222").create()
        filter(record(value = "jcvewtvu6c")) shouldBe true
        filter(record(value = """{"key":111222333}""")) shouldBe true
        filter(record(value = """{"foo":111222333}""")) shouldBe false
        filter(record(value = """{"foo":null}""")) shouldBe true
        filter(record(value = """{"foo":444555666}""")) shouldBe false
    }

    @Test
    fun `field not contains array number`() {
        val filter = valueFilter("foo", NOT_CONTAINS, "222").create()
        filter(record(value = "jcvewtvu6c")) shouldBe true
        filter(record(value = """{"key":[111,222,333]}""")) shouldBe true
        filter(record(value = """{"foo":[111,222,333]}""")) shouldBe false
        filter(record(value = """{"foo":null}""")) shouldBe true
        filter(record(value = """{"foo":[444,555,666]}""")) shouldBe true
    }

    @Test
    fun `field not contains array string`() {
        val filter = valueFilter("foo", NOT_CONTAINS, "bbb").create()
        filter(record(value = "jcvewtvu6c")) shouldBe true
        filter(record(value = """{"key":["aaa","bbb","ccc"]}""")) shouldBe true
        filter(record(value = """{"foo":["aaa","bbb","ccc"]}""")) shouldBe false
        filter(record(value = """{"foo":null}""")) shouldBe true
        filter(record(value = """{"foo":["xxx","yyy","zzz"]}""")) shouldBe true
    }

    @Test
    fun `field not contains array map`() {
        val filter = valueFilter("foo", NOT_CONTAINS, "{x=1, y=2}").create()
        filter(record(value = "jcvewtvu6c")) shouldBe true
        filter(record(value = """{"key":[{"x":1,"y":2},{"x":3,"y":4}]}""")) shouldBe true
        filter(record(value = """{"foo":[{"x":1,"y":2},{"x":3,"y":4}]}""")) shouldBe false
        filter(record(value = """{"foo":null}""")) shouldBe true
        filter(record(value = """{"foo":[{"x":5,"y":6},{"x":7,"y":8}]}""")) shouldBe true
    }

    @Test
    fun `field regex`() {
        val filter = valueFilter("foo", REGEX, "[A-Z]+-[0-9]+").create()
        filter(record(value = "jcvewtvu6c")) shouldBe false
        filter(record(value = """{"key":"Jira: ABC-123"}""")) shouldBe false
        filter(record(value = """{"foo":"Jira: ABC-123"}""")) shouldBe true
        filter(record(value = """{"foo":null}""")) shouldBe false
        filter(record(value = """{"foo":"Jira: without id"}""")) shouldBe false
    }

    @Test
    fun `field not regex`() {
        val filter = valueFilter("foo", NOT_REGEX, "[A-Z]+-[0-9]+").create()
        filter(record(value = "jcvewtvu6c")) shouldBe true
        filter(record(value = """{"key":"Jira: ABC-123"}""")) shouldBe true
        filter(record(value = """{"foo":"Jira: ABC-123"}""")) shouldBe false
        filter(record(value = """{"foo":null}""")) shouldBe true
        filter(record(value = """{"foo":"Jira: without id"}""")) shouldBe true
    }

    @Test
    fun `header exist`() {
        val filter = headerFilter("foo", EXIST).create()
        filter(record(headers = emptyMap())) shouldBe false
        filter(record(headers = mapOf("foo" to "abc"))) shouldBe true
        filter(record(headers = mapOf("bar" to "abc"))) shouldBe false
    }

    @Test
    fun `header equal to`() {
        val filter = headerFilter("foo", EQUAL_TO, "abc").create()
        filter(record(headers = emptyMap())) shouldBe false
        filter(record(headers = mapOf("foo" to "abc"))) shouldBe true
        filter(record(headers = mapOf("bar" to "abc"))) shouldBe false
        filter(record(headers = mapOf("foo" to "def"))) shouldBe false
        filter(record(headers = mapOf("bar" to "def"))) shouldBe false
    }

    @Test
    fun `composite all`() {
        val filter = (headerFilter("x", EXIST) and valueFilter("a", EXIST)).create()
        filter(record(headers = mapOf(), value = """{}""")) shouldBe false
        filter(record(headers = mapOf(), value = """{"a":1}""")) shouldBe false
        filter(record(headers = mapOf("x" to "."), value = """{}""")) shouldBe false
        filter(record(headers = mapOf("x" to "."), value = """{"a":1}""")) shouldBe true
    }

    @Test
    fun `composite any`() {
        val filter = (headerFilter("x", EXIST) or valueFilter("a", EXIST)).create()
        filter(record(headers = mapOf(), value = """{}""")) shouldBe false
        filter(record(headers = mapOf(), value = """{"a":1}""")) shouldBe true
        filter(record(headers = mapOf("x" to "."), value = """{}""")) shouldBe true
        filter(record(headers = mapOf("x" to "."), value = """{"a":1}""")) shouldBe true
    }

    @Test
    fun `composite none`() {
        val filter = (headerFilter("x", EXIST) nor valueFilter("a", EXIST)).create()
        filter(record(headers = mapOf(), value = """{}""")) shouldBe true
        filter(record(headers = mapOf(), value = """{"a":1}""")) shouldBe false
        filter(record(headers = mapOf("x" to "."), value = """{}""")) shouldBe false
        filter(record(headers = mapOf("x" to "."), value = """{"a":1}""")) shouldBe false
    }

    @Test
    fun `composite mixed`() {
        val xExist = headerFilter("x", EXIST)
        val aExist = valueFilter("a", EXIST)
        val filter = ((xExist and !aExist) or (!xExist and aExist)).create()
        filter(record(headers = mapOf(), value = """{}""")) shouldBe false
        filter(record(headers = mapOf(), value = """{"a":1}""")) shouldBe true
        filter(record(headers = mapOf("x" to "."), value = """{}""")) shouldBe true
        filter(record(headers = mapOf("x" to "."), value = """{"a":1}""")) shouldBe false
    }

    @Test
    fun `nested map equals`() {
        val filter = valueFilter("a.x", EQUAL_TO, "1").create()
        filter(record(value = """{"a":{"x":1}}""")) shouldBe true
        filter(record(value = """{"a":{"x":2}}""")) shouldBe false
        filter(record(value = """{"a":{"y":1}}""")) shouldBe false
        filter(record(value = """{"b":{"x":1}}""")) shouldBe false
        filter(record(value = """{"a":1}""")) shouldBe false
    }

    @Test
    fun `nested map any key equals 1`() {
        val filter = valueFilter("a.*", EQUAL_TO, "1").create()
        filter(record(value = """{"a":{"x":1}}""")) shouldBe true
        filter(record(value = """{"a":{"x":2}}""")) shouldBe false
        filter(record(value = """{"a":{"y":1}}""")) shouldBe true
        filter(record(value = """{"b":{"x":1}}""")) shouldBe false
        filter(record(value = """{"a":1}""")) shouldBe false
    }

    @Test
    fun `nested map any key equals 2`() {
        val filter = valueFilter("*.x", EQUAL_TO, "1").create()
        filter(record(value = """{"a":{"x":1}}""")) shouldBe true
        filter(record(value = """{"a":{"x":2}}""")) shouldBe false
        filter(record(value = """{"a":{"y":1}}""")) shouldBe false
        filter(record(value = """{"b":{"x":1}}""")) shouldBe true
        filter(record(value = """{"a":1}""")) shouldBe false
    }

    @Test
    fun `nested map any key equals 3`() {
        val filter = valueFilter("*.*", EQUAL_TO, "1").create()
        filter(record(value = """{"a":{"x":1}}""")) shouldBe true
        filter(record(value = """{"a":{"x":2}}""")) shouldBe false
        filter(record(value = """{"a":{"y":1}}""")) shouldBe true
        filter(record(value = """{"b":{"x":1}}""")) shouldBe true
        filter(record(value = """{"a":1}""")) shouldBe false
    }

    @Test
    fun `nested list equals`() {
        val filter = valueFilter("a[1]", EQUAL_TO, "9").create()
        filter(record(value = """{"a":[4,9]}""")) shouldBe true
        filter(record(value = """{"a":[9,4]}""")) shouldBe false
        filter(record(value = """{"a":[9]}""")) shouldBe false
        filter(record(value = """{"a":[]}""")) shouldBe false
        filter(record(value = """{"b":[4,9]}""")) shouldBe false
        filter(record(value = """{"a":1}""")) shouldBe false
    }

    @Test
    fun `nested map any index equals`() {
        val filter = valueFilter("a[*]", EQUAL_TO, "9").create()
        filter(record(value = """{"a":[4,9]}""")) shouldBe true
        filter(record(value = """{"a":[9,4]}""")) shouldBe true
        filter(record(value = """{"a":[9]}""")) shouldBe true
        filter(record(value = """{"a":[]}""")) shouldBe false
        filter(record(value = """{"b":[4,9]}""")) shouldBe false
        filter(record(value = """{"a":1}""")) shouldBe false
    }

    @Test
    fun `nested map any index equals 1x`() {
        val filter = valueFilter("[1].x", EQUAL_TO, "9").create()
        filter(record(value = """[{"x":4},{"x":9}]""")) shouldBe true
        filter(record(value = """[{"x":4},{"y":9}]""")) shouldBe false
        filter(record(value = """[{"x":9},{"x":4}]""")) shouldBe false
        filter(record(value = """[{"x":9},{"y":4}]""")) shouldBe false
        filter(record(value = """[{"x":4}]""")) shouldBe false
    }

    @Test
    fun `nested map any index equals (star)x`() {
        val filter = valueFilter("[*].x", EQUAL_TO, "9").create()
        filter(record(value = """[{"x":4},{"x":9}]""")) shouldBe true
        filter(record(value = """[{"x":4},{"y":9}]""")) shouldBe false
        filter(record(value = """[{"x":9},{"x":4}]""")) shouldBe true
        filter(record(value = """[{"x":9},{"y":4}]""")) shouldBe true
        filter(record(value = """[{"x":4}]""")) shouldBe false
    }

    @Test
    fun `nested map any index equals (star)(star)`() {
        val filter = valueFilter("[*].*", EQUAL_TO, "9").create()
        filter(record(value = """[{"x":4},{"x":9}]""")) shouldBe true
        filter(record(value = """[{"x":4},{"y":9}]""")) shouldBe true
        filter(record(value = """[{"x":9},{"x":4}]""")) shouldBe true
        filter(record(value = """[{"x":9},{"y":4}]""")) shouldBe true
        filter(record(value = """[{"x":4}]""")) shouldBe false
    }

    @Test
    fun `object any field`() {
        val filter = valueFilter("*", EQUAL_TO, "9").create()
        filter(record(value = """{"x":4}""")) shouldBe false
        filter(record(value = """{"x":9}""")) shouldBe true
        filter(record(value = """[4,5]""")) shouldBe false
        filter(record(value = """[5,9]""")) shouldBe false
    }

    @Test
    fun `array any element`() {
        val filter = valueFilter("[*]", EQUAL_TO, "9").create()
        filter(record(value = """{"x":4}""")) shouldBe false
        filter(record(value = """{"x":9}""")) shouldBe false
        filter(record(value = """[4,5]""")) shouldBe false
        filter(record(value = """[5,9]""")) shouldBe true
    }

    private fun record(
            headers: Map<String, String> = emptyMap(),
            value: String = ""
    ): KafkaRecord {
        return recordFactory.creatorFor("t", ClusterRef("c"), RecordDeserialization.ANY).create(ConsumerRecord(
                "",
                0,
                0,
                0L,
                TimestampType.LOG_APPEND_TIME,
                0,
                0,
                ByteArray(0),
                value.toByteArray(Charsets.UTF_8),
                RecordHeaders(headers.map { RecordHeader(it.key, it.value.toByteArray(Charsets.UTF_8)) }),
                Optional.empty(),
        ))
    }

    private fun valueFilter(field: String, rule: FieldRuleType, value: String = ""): ReadFilter {
        return ReadFilter(jsonValueRule = ValueRule(
                name = field, type = rule, value = value
        ))
    }

    private fun headerFilter(field: String, rule: FieldRuleType, value: String = ""): ReadFilter {
        return ReadFilter(headerValueRule = ValueRule(
                name = field, type = rule, value = value
        ))
    }

    private fun ReadFilter.create() = filterFactory.createFilter(this)

    private infix fun ReadFilter.and(other: ReadFilter) = ReadFilter(all = listOf(this, other))
    private infix fun ReadFilter.or(other: ReadFilter) = ReadFilter(any = listOf(this, other))
    private infix fun ReadFilter.nor(other: ReadFilter) = ReadFilter(none = listOf(this, other))
    private operator fun ReadFilter.not() = ReadFilter(none = listOf(this))

}