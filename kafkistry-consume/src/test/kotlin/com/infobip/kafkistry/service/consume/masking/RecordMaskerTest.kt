package com.infobip.kafkistry.service.consume.masking

import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.model.TopicName
import com.infobip.kafkistry.service.consume.JsonPathDef
import com.infobip.kafkistry.service.consume.filter.JsonPathParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RecordMaskerTest {

    private fun valueMaskerFor(vararg paths: JsonPathDef): RecordMasker {
        return maskerFor(maskSpec(valuePathDefs = setOf(*paths)))
    }

    private fun keyMaskerFor(vararg paths: JsonPathDef): RecordMasker {
        return maskerFor(maskSpec(keyPathDefs = setOf(*paths)))
    }

    private fun headerMaskerFor(name: String, vararg paths: JsonPathDef): RecordMasker {
        return maskerFor(maskSpec(headerPathDefs = mapOf(name to setOf(*paths))))
    }

    private fun maskSpec(
        valuePathDefs: Set<JsonPathDef> = emptySet(),
        keyPathDefs: Set<JsonPathDef> = emptySet(),
        headerPathDefs: Map<String, Set<JsonPathDef>> = emptyMap(),
    ) = TopicMaskingSpec(keyPathDefs, valuePathDefs, headerPathDefs)

    private fun maskerFor(spec: TopicMaskingSpec): RecordMasker {
        val ruleProvider = object : RecordMaskingRuleProvider {
            override fun maskingSpecFor(topic: TopicName, clusterRef: ClusterRef) = listOf(spec)
        }
        return RecordMaskerFactory(
            maskingRulesProviders = listOf(ruleProvider),
            jsonPathParser = JsonPathParser(),
        ).createMaskerFor("", ClusterRef(""))
    }

    @Test
    fun `mask intention nothing`() {
        val masker = maskerFor(maskSpec())
        assertThat(masker.masksValue()).isFalse
        assertThat(masker.masksKey()).isFalse
        assertThat(masker.masksHeader("h")).isFalse
    }

    @Test
    fun `mask intention key`() {
        val masker = maskerFor(maskSpec(keyPathDefs = setOf("k")))
        assertThat(masker.masksValue()).isFalse
        assertThat(masker.masksKey()).isTrue
        assertThat(masker.masksHeader("h")).isFalse
    }

    @Test
    fun `mask intention value`() {
        val masker = maskerFor(maskSpec(valuePathDefs = setOf("v")))
        assertThat(masker.masksValue()).isTrue
        assertThat(masker.masksKey()).isFalse
        assertThat(masker.masksHeader("h")).isFalse
    }

    @Test
    fun `mask intention header`() {
        val masker = maskerFor(maskSpec(headerPathDefs = mapOf("h" to setOf("h"))))
        assertThat(masker.masksValue()).isFalse
        assertThat(masker.masksKey()).isFalse
        assertThat(masker.masksHeader("h")).isTrue
        assertThat(masker.masksHeader("o")).isFalse
    }

    @Test
    fun `mask field in json`() {
        val masked = valueMaskerFor("name").maskValue(
            mapOf("id" to 4, "name" to "personal data")
        )
        assertThat(masked).isEqualTo(mapOf("id" to 4, "name" to "***MASKED***"))
    }

    @Test
    fun `mask 2 fields in json`() {
        val masked = valueMaskerFor("name", "id").maskValue(
            mapOf("id" to 4, "name" to "personal data")
        )
        assertThat(masked).isEqualTo(mapOf("id" to "***MASKED***", "name" to "***MASKED***"))
    }

    @Test
    fun `mask field in json hat doesn't exist`() {
        val masked = valueMaskerFor("non-existing").maskValue(
            mapOf("id" to 4, "name" to "personal data")
        )
        assertThat(masked).isEqualTo(mapOf("id" to 4, "name" to "personal data"))
    }

    @Test
    fun `mask nothing`() {
        val masked = valueMaskerFor().maskValue(
            mapOf("id" to 4, "name" to "personal data")
        )
        assertThat(masked).isEqualTo(mapOf("id" to 4, "name" to "personal data"))
    }

    @Test
    fun `mask whole value`() {
        val masked = valueMaskerFor("").maskValue(
            mapOf("id" to 4, "name" to "personal data")
        )
        assertThat(masked).isEqualTo("***MASKED***")
    }

    @Test
    fun `mask field in array`() {
        val masked = valueMaskerFor("arr[*].name").maskValue(
            mapOf(
                "id" to 4,
                "arr" to listOf(
                    mapOf("id" to 1, "name" to "foo"),
                    mapOf("id" to 2, "name" to "bar"),
                )
            )
        )
        assertThat(masked).isEqualTo(mapOf(
            "id" to 4,
            "arr" to listOf(
                mapOf("id" to 1, "name" to "***MASKED***"),
                mapOf("id" to 2, "name" to "***MASKED***"),
            )
        ))
    }

    @Test
    fun `mask value complex 1`() {
        val masked = valueMaskerFor(
            "secret",
            "*.email",
            "object.address",
            "child.*",
            "arr[*].name",
            "arr[2].age",
            "arr[3]",
            "arr[4].*",
        ).maskValue(
            mapOf(
                "id" to 4,
                "secret" to "plaintext",
                "object" to mapOf(
                    "type" to "X",
                    "email" to "x@kr.com",
                    "address" to "address x",
                ),
                "parent" to mapOf(
                    "type" to "Y",
                    "email" to "y@kr.com",
                    "address" to "address y",
                ),
                "child" to mapOf(
                    "type" to "Z",
                    "email" to "z@kr.com",
                    "address" to "address z",
                ),
                "arr" to listOf(
                    mapOf("id" to 1, "name" to "foo", "age" to 25),
                    mapOf("id" to 2, "name" to "bar", "age" to 45),
                    mapOf("id" to 3, "name" to "baz", "age" to 55),
                    mapOf("id" to 4, "name" to "bat", "age" to 18),
                    mapOf("id" to 5, "name" to "bak", "age" to 32),
                )
            )
        )
        assertThat(masked).isEqualTo(mapOf(
            "id" to 4,
            "secret" to "***MASKED***",
            "object" to mapOf(
                "type" to "X",
                "email" to "***MASKED***",
                "address" to "***MASKED***",
            ),
            "parent" to mapOf(
                "type" to "Y",
                "email" to "***MASKED***",
                "address" to "address y",
            ),
            "child" to mapOf(
                "type" to "***MASKED***",
                "email" to "***MASKED***",
                "address" to "***MASKED***",
            ),
            "arr" to listOf(
                mapOf("id" to 1, "name" to "***MASKED***", "age" to 25),
                mapOf("id" to 2, "name" to "***MASKED***", "age" to 45),
                mapOf("id" to 3, "name" to "***MASKED***", "age" to "***MASKED***"),
                "***MASKED***",
                mapOf("id" to "***MASKED***", "name" to "***MASKED***", "age" to "***MASKED***"),
            )
        ))
    }

    @Test
    fun `mask field in key`() {
        val masked = keyMaskerFor("name").maskKey(
            mapOf("id" to 4, "name" to "personal data")
        )
        assertThat(masked).isEqualTo(mapOf("id" to 4, "name" to "***MASKED***"))
    }

    @Test
    fun `mask whole key`() {
        val masked = keyMaskerFor("").maskKey(
            "some-secret"
        )
        assertThat(masked).isEqualTo("***MASKED***")
    }

    @Test
    fun `mask whole header`() {
        val masked = headerMaskerFor("KFK_HEADER", "").maskHeader(
            "KFK_HEADER","some-secret"
        )
        assertThat(masked).isEqualTo("***MASKED***")
    }

    @Test
    fun `dont mask other header`() {
        val masked = headerMaskerFor("OTHER", "").maskHeader(
            "KFK_HEADER","not-secret"
        )
        assertThat(masked).isEqualTo("not-secret")
    }

}