package com.infobip.kafkistry.recordstructure

import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.model.RecordFieldType
import org.junit.jupiter.api.Test

class AnalyzeFilterTest {

    @Test
    fun `path - empty filter`() {
        val filter = AnalyzeFilter(RecordAnalyzerProperties())
        assertThat(filter.shouldSampleValuesForPath(emptyList())).isTrue
        assertThat(filter.shouldSampleValuesForPath(listOf(
            RecordFieldType.STRING to "field",
        ))).isTrue
        assertThat(filter.shouldSampleValuesForPath(listOf(
            RecordFieldType.OBJECT to "object",
            RecordFieldType.INTEGER to "subField",
        ))).isTrue
    }

    @Test
    fun `path - single included`() {
        val filter = AnalyzeFilter(RecordAnalyzerProperties().apply {
            valueSampling.includedFields = setOf("testField")
        })
        assertThat(filter.shouldSampleValuesForPath(emptyList())).isFalse
        assertThat(filter.shouldSampleValuesForPath(listOf(
            RecordFieldType.STRING to "otherField",
        ))).isFalse
        assertThat(filter.shouldSampleValuesForPath(listOf(
            RecordFieldType.STRING to "testField",
        ))).isTrue
        assertThat(filter.shouldSampleValuesForPath(listOf(
            RecordFieldType.OBJECT to "object",
            RecordFieldType.INTEGER to "subField",
        ))).isFalse
    }

    @Test
    fun `path - multiple included`() {
        val filter = AnalyzeFilter(RecordAnalyzerProperties().apply {
            valueSampling.includedFields = setOf("field", "object.field", "varObject.*.specific")
        })
        assertThat(filter.shouldSampleValuesForPath(emptyList())).isFalse
        assertThat(filter.shouldSampleValuesForPath(listOf(
            RecordFieldType.STRING to "other",
        ))).isFalse
        assertThat(filter.shouldSampleValuesForPath(listOf(
            RecordFieldType.STRING to "field",
        ))).isTrue
        assertThat(filter.shouldSampleValuesForPath(listOf(
            RecordFieldType.OBJECT to "object",
            RecordFieldType.INTEGER to "other",
        ))).isFalse
        assertThat(filter.shouldSampleValuesForPath(listOf(
            RecordFieldType.OBJECT to "object",
            RecordFieldType.INTEGER to "field",
        ))).isTrue
        assertThat(filter.shouldSampleValuesForPath(listOf(
            RecordFieldType.OBJECT to "other",
            RecordFieldType.INTEGER to "field",
        ))).isFalse
        assertThat(filter.shouldSampleValuesForPath(listOf(
            RecordFieldType.OBJECT to "varObject",
            RecordFieldType.OBJECT to "a",
            RecordFieldType.BOOLEAN to "specific",
        ))).isTrue
        assertThat(filter.shouldSampleValuesForPath(listOf(
            RecordFieldType.OBJECT to "varObject",
            RecordFieldType.OBJECT to "b",
            RecordFieldType.BOOLEAN to "specific",
        ))).isTrue
        assertThat(filter.shouldSampleValuesForPath(listOf(
            RecordFieldType.OBJECT to "varObject",
            RecordFieldType.OBJECT to "x",
            RecordFieldType.BOOLEAN to "other",
        ))).isFalse
        assertThat(filter.shouldSampleValuesForPath(listOf(
            RecordFieldType.OBJECT to "other",
            RecordFieldType.OBJECT to "x",
            RecordFieldType.BOOLEAN to "specific",
        ))).isFalse
    }

}