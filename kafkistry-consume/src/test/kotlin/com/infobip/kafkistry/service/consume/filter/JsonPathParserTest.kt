package com.infobip.kafkistry.service.consume.filter

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class JsonPathParserTest {

    private val parser = JsonPathParser()

    @Test
    fun empty() {
        assertThat(parser.parseJsonKeyPath("")).isEmpty()
    }

    @Test
    fun `single map key`() {
        assertThat(parser.parseJsonKeyPath("foo")).isEqualTo(listOf(
            MapKeyPathElement("foo")
        ))
    }

    @Test
    fun `single any map key`() {
        assertThat(parser.parseJsonKeyPath("*")).isEqualTo(listOf(
            MapKeyPathElement(null)
        ))
    }

    @Test
    fun `single list index`() {
        assertThat(parser.parseJsonKeyPath("[42]")).isEqualTo(listOf(
            ListIndexPathElement(42)
        ))
    }

    @Test
    fun `single any list index`() {
        assertThat(parser.parseJsonKeyPath("[*]")).isEqualTo(listOf(
            ListIndexPathElement(null)
        ))
    }

    @Test
    fun `nested map keys`() {
        assertThat(parser.parseJsonKeyPath("foo.bar")).isEqualTo(listOf(
            MapKeyPathElement("foo"),
            MapKeyPathElement("bar"),
        ))
    }

    @Test
    fun `escaped map key`() {
        assertThat(parser.parseJsonKeyPath("com\\.foo\\.bar")).isEqualTo(listOf(
            MapKeyPathElement("com.foo.bar"),
        ))
    }

    @Test
    fun `escaped list`() {
        assertThat(parser.parseJsonKeyPath("foo\\[*]")).isEqualTo(listOf(
            MapKeyPathElement("foo[*]"),
        ))
    }

    @Test
    fun `escaped list alone`() {
        assertThat(parser.parseJsonKeyPath("\\[*]")).isEqualTo(listOf(
            MapKeyPathElement("[*]"),
        ))
    }

    @Test
    fun `nested map keys any 1`() {
        assertThat(parser.parseJsonKeyPath("foo.*.bar")).isEqualTo(listOf(
            MapKeyPathElement("foo"),
            MapKeyPathElement(null),
            MapKeyPathElement("bar"),
        ))
    }

    @Test
    fun `nested map keys any 2`() {
        assertThat(parser.parseJsonKeyPath("*.foo.*")).isEqualTo(listOf(
            MapKeyPathElement(null),
            MapKeyPathElement("foo"),
            MapKeyPathElement(null),
        ))
    }

    @Test
    fun `nested map specific list`() {
        assertThat(parser.parseJsonKeyPath("foo[55]")).isEqualTo(listOf(
            MapKeyPathElement("foo"),
            ListIndexPathElement(55),
        ))
    }

    @Test
    fun `nested map any list`() {
        assertThat(parser.parseJsonKeyPath("foo[*]")).isEqualTo(listOf(
            MapKeyPathElement("foo"),
            ListIndexPathElement(null),
        ))
    }

    @Test
    fun `nested map specific list key`() {
        assertThat(parser.parseJsonKeyPath("foo[55].bar")).isEqualTo(listOf(
            MapKeyPathElement("foo"),
            ListIndexPathElement(55),
            MapKeyPathElement("bar"),
        ))
    }

    @Test
    fun `nested map any list map`() {
        assertThat(parser.parseJsonKeyPath("foo[*].bar")).isEqualTo(listOf(
            MapKeyPathElement("foo"),
            ListIndexPathElement(null),
            MapKeyPathElement("bar"),
        ))
    }

    @Test
    fun `nested map any list any map key`() {
        assertThat(parser.parseJsonKeyPath("foo[*].*")).isEqualTo(listOf(
            MapKeyPathElement("foo"),
            ListIndexPathElement(null),
            MapKeyPathElement(null),
        ))
    }

    @Test
    fun `nested map any list any list`() {
        assertThat(parser.parseJsonKeyPath("foo[*][*]")).isEqualTo(listOf(
            MapKeyPathElement("foo"),
            ListIndexPathElement(null),
            ListIndexPathElement(null),
        ))
    }

    @Test
    fun `long example 1`() {
        assertThat(parser.parseJsonKeyPath("*.*[*][*].*[11].x[22][*].y.z")).isEqualTo(listOf(
            MapKeyPathElement(null),
            MapKeyPathElement(null),
            ListIndexPathElement(null),
            ListIndexPathElement(null),
            MapKeyPathElement(null),
            ListIndexPathElement(11),
            MapKeyPathElement("x"),
            ListIndexPathElement(22),
            ListIndexPathElement(null),
            MapKeyPathElement("y"),
            MapKeyPathElement("z"),
        ))
    }




}