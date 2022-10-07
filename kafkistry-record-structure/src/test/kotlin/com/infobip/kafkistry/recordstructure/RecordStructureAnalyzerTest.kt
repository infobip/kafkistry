package com.infobip.kafkistry.recordstructure

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.model.*
import com.infobip.kafkistry.utils.test.newTestFolder
import org.junit.Test
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Function
import kotlin.system.measureTimeMillis

class RecordStructureAnalyzerTest {

    companion object {
        private val CLUSTER = ClusterRef("test-cluster")
        private const val TOPIC = "test-topic"
    }

    private val emptyHeaders: List<RecordField> = listOf(
        RecordField(
            null,
            null,
            RecordFieldType.OBJECT,
            emptyList()
        )
    )

    private fun newAnalyzer(
        storageDir: String = newTestFolder("record-analyzer-storage"),
        timeWindow: Long = TimeUnit.MINUTES.toMillis(1),
        cardinalityMagnitudeFactorThreshold: Int = 10,
        valueSamplingEnabled: Boolean = false,
        valSamplingMaxCardinality: Int = 10,
        valueSampleIncludedTopics: String? = null,
        valueSampleExcludedTopics: String? = null,
    ): RecordStructureAnalyzer {
        val properties = RecordAnalyzerProperties().apply {
            this.storageDir = storageDir
            this.timeWindow = timeWindow
            this.cardinalityMagnitudeFactorThreshold = cardinalityMagnitudeFactorThreshold
            this.valueSampling.apply {
                enabled = valueSamplingEnabled
                maxCardinality = valSamplingMaxCardinality
                enabledOn.topics.included = valueSampleIncludedTopics?.split(",")?.toSet().orEmpty()
                enabledOn.topics.excluded = valueSampleExcludedTopics?.split(",")?.toSet().orEmpty()
            }
        }
        return RecordStructureAnalyzer(
            properties = properties,
            recordStructureAnalyzerStorage = DirectoryRecordStructureAnalyzerStorage(properties),
            analyzeFilter = AnalyzeFilter(properties),
        )
    }

    private fun RecordStructureAnalyzer.acceptRecord(payload: String?, headers: Map<String, String?> = emptyMap()) {
        analyzeRecord(
            CLUSTER, ConsumerRecord(
                TOPIC, 0, 0, 0, TimestampType.CREATE_TIME,
                0, 0,
                byteArrayOf(0x0), payload?.encodeToByteArray(),
                RecordHeaders(headers.map { RecordHeader(it.key, it.value?.encodeToByteArray()) }),
                Optional.empty(),
            )
        )
    }

    private fun RecordStructureAnalyzer.structure() = getStructure(CLUSTER.identifier, TOPIC)
    private fun RecordStructureAnalyzer.allStructures() = getAllStructures(CLUSTER.identifier)

    private fun assertJsonStructure(expectedStructure: RecordsStructure, vararg jsons: String?) {
        newAnalyzer().assertJsonStructure(expectedStructure, *jsons)
    }

    private fun RecordStructureAnalyzer.assertJsonStructure(
        expectedStructure: RecordsStructure,
        vararg jsons: String?
    ) {
        jsons.forEach { acceptRecord(it) }
        dump()
        load()
        val structure = structure()
        structure.assertEqualsTo(expectedStructure)
    }

    private fun RecordsStructure?.assertEqualsTo(
        expectedStructure: RecordsStructure?
    ) {
        val mapper = ObjectMapper().writerWithDefaultPrettyPrinter()
        assertThat(mapper.writeValueAsString(this)).isEqualTo(mapper.writeValueAsString(expectedStructure))
        assertThat(this).isEqualTo(expectedStructure)
    }

    @Test
    fun `test nothing sampled`() {
        with(newAnalyzer()) {
            assertThat(structure()).isNull()
        }
    }

    @Test
    fun `test not a json`() {
        with(newAnalyzer()) {
            acceptRecord("not json")
            val structure = structure()
            assertThat(structure)
                .extracting { it?.payloadType }
                .isEqualTo(PayloadType.UNKNOWN)
            assertThat(structure)
                .extracting { it?.jsonFields }
                .isEqualTo(null)
            assertThat(structure)
                .extracting { it?.nullable }
                .isEqualTo(false)
        }
    }

    @Test
    fun `test one complex json`() {
        with(newAnalyzer()) {
            acceptRecord("""{"text":"Foo", "number":123, "subJson": { "subvalue": [567, 567, 567] }, "values": [123, 123, 123]}""")
            val structure = structure()
            assertThat(structure)
                .extracting { it?.payloadType }
                .isEqualTo(PayloadType.JSON)
            assertThat(structure?.jsonFields).hasSize(1)
            assertThat(structure?.jsonFields?.get(0)?.type).isEqualTo(RecordFieldType.OBJECT)
            assertThat(structure?.jsonFields?.get(0)?.name).isNull()
            assertThat(structure?.jsonFields?.get(0)?.children)
                .extracting(Function { it.name to it.type })
                .containsExactlyInAnyOrder(
                    "text" to RecordFieldType.STRING,
                    "number" to RecordFieldType.INTEGER,
                    "subJson" to RecordFieldType.OBJECT,
                    "values" to RecordFieldType.ARRAY,
                )
            assertThat(structure?.jsonFields?.get(0)?.children)
                .extracting(Function { it.name to it.children })
                .containsExactlyInAnyOrder(
                    "text" to null,
                    "number" to null,
                    "subJson" to mutableListOf(
                        RecordField(
                            name = "subvalue",
                            fullName = "subJson.subvalue",
                            type = RecordFieldType.ARRAY,
                            mutableListOf(
                                RecordField(
                                    name = null,
                                    fullName = "subJson.subvalue[*]",
                                    type = RecordFieldType.INTEGER,
                                    children = null
                                )
                            )
                        )
                    ),
                    "values" to mutableListOf(
                        RecordField(
                            name = null,
                            fullName = "values[*]",
                            type = RecordFieldType.INTEGER,
                            children = null
                        )
                    ),
                )
            assertThat(allStructures()).isNotNull
        }
    }

    @Test
    fun `test several complex json`() {
        with(newAnalyzer()) {
            acceptRecord("broken not a json record")
            acceptRecord("""{"text": "Foo", "number": 123, "subJson": { "subvalue": ["string", "one_more_string", ""] }, "values": [123, 123, 123]}""")
            acceptRecord("""{"another_text": "Foo", "another_number": 123, "subJson": { "subvalue": [100, 100, 100] }, "values": [489, 489, 489]}""")
            acceptRecord("broken not a json record")
            val structure = structure()
            assertThat(structure)
                .extracting { it?.payloadType }
                .isEqualTo(PayloadType.JSON)
            assertThat(structure?.jsonFields).hasSize(1)
            assertThat(structure?.jsonFields?.get(0)?.type).isEqualTo(RecordFieldType.OBJECT)
            assertThat(structure?.jsonFields?.get(0)?.name).isNull()
            assertThat(structure?.jsonFields?.get(0)?.children)
                .extracting(Function { it.name to it.type })
                .containsExactlyInAnyOrder(
                    "text" to RecordFieldType.STRING,
                    "another_text" to RecordFieldType.STRING,
                    "number" to RecordFieldType.INTEGER,
                    "another_number" to RecordFieldType.INTEGER,
                    "subJson" to RecordFieldType.OBJECT,
                    "values" to RecordFieldType.ARRAY,
                )
            assertThat(structure?.jsonFields?.get(0)?.children)
                .extracting(Function { it.name to it.children })
                .containsExactlyInAnyOrder(
                    "text" to null,
                    "another_text" to null,
                    "number" to null,
                    "another_number" to null,
                    "subJson" to mutableListOf(
                        RecordField(
                            "subvalue",
                            "subJson.subvalue",
                            RecordFieldType.ARRAY,
                            mutableListOf(
                                RecordField(
                                    null,
                                    "subJson.subvalue[*]",
                                    RecordFieldType.STRING,
                                    null
                                ),
                                RecordField(
                                    null,
                                    "subJson.subvalue[*]",
                                    RecordFieldType.INTEGER,
                                    null
                                ),
                            )
                        )
                    ),
                    "values" to mutableListOf(
                        RecordField(
                            name = null,
                            fullName = "values[*]",
                            type = RecordFieldType.INTEGER,
                            children = null,
                            nullable = false
                        )
                    ),
                )
            assertThat(allStructures()).isNotNull
        }
    }

    @Test
    fun `test json dump and load`() {
        with(newAnalyzer()) {
            acceptRecord("""{"text":"Foo", "number":123, "subJson": { "subvalue": [123, 456, 789] }, "values": [123, 123, 123]}""")
            this.dump()
            this.load()
            val structure = structure()
            assertThat(structure != null)
            assertThat(structure?.jsonFields).hasSize(1)
            assertThat(structure?.jsonFields?.get(0)?.type).isEqualTo(RecordFieldType.OBJECT)
            assertThat(structure?.jsonFields?.get(0)?.name).isNull()
            assertThat(structure?.jsonFields?.get(0)?.children)
                .extracting(Function { it.name to it.type })
                .containsExactlyInAnyOrder(
                    "text" to RecordFieldType.STRING,
                    "number" to RecordFieldType.INTEGER,
                    "subJson" to RecordFieldType.OBJECT,
                    "values" to RecordFieldType.ARRAY,
                )
            assertThat(structure?.jsonFields?.get(0)?.children)
                .extracting(Function { it.name to it.children })
                .containsExactlyInAnyOrder(
                    "text" to null,
                    "number" to null,
                    "subJson" to mutableListOf(
                        RecordField(
                            "subvalue",
                            "subJson.subvalue",
                            RecordFieldType.ARRAY,
                            children = mutableListOf(
                                RecordField(
                                    null,
                                    "subJson.subvalue[*]",
                                    RecordFieldType.INTEGER,
                                    null
                                )
                            )
                        )
                    ),
                    "values" to mutableListOf(
                        RecordField(
                            null,
                            "values[*]",
                            RecordFieldType.INTEGER,
                            null
                        )
                    ),
                )
            assertThat(allStructures()).isNotNull
        }
    }

    @Test
    fun `test json with field of array of jsons`() {
        val json = """
            {
                "arr": [
                    {
                        "k": "v"
                    }
                ]
            }
        """.trimIndent()
        val expectedStructure = RecordsStructure(
            payloadType = PayloadType.JSON,
            headerFields = emptyHeaders,
            jsonFields = listOf(
                RecordField(
                    name = null,
                    fullName = null,
                    type = RecordFieldType.OBJECT,
                    children = listOf(
                        RecordField(
                            name = "arr",
                            fullName = "arr",
                            type = RecordFieldType.ARRAY,
                            children = listOf(
                                RecordField(
                                    name = null,
                                    fullName = "arr[*]",
                                    type = RecordFieldType.OBJECT,
                                    children = listOf(
                                        RecordField(
                                            name = "k",
                                            fullName = "arr[*].k",
                                            type = RecordFieldType.STRING
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
        assertJsonStructure(expectedStructure, json)
    }

    private val jsonRealisticExample = """
            {
                "accountId": 123456,
                "action": "MODIFICATION",
                "personModifications": [
                    {
                        "id": 42,
                        "firstName": "Antonio",
                        "labels": [
                            "111",
                            "222"
                        ],
                        "officeLocations": [
                            {
                                "id": 10007,
                                "type": "OPEN_SPACE",
                                "headquarter": false
                            }
                        ],
                        "attributes": {
                            "d4dd1234-ccdd-1024-2048-112233445566": "https://example.com"
                        },
                        "modifiedAt": 1612345678900,
                        "modifiedFrom": "GUI",
                        "systemAttributes": {
                            "lastActive": 1617172253364
                        }
                    }
                ],
                "attributesMetadata": {
                    "d4dd1234-ccdd-1024-2048-112233445566": "URL"
                },
                "labels": {
                    "111": {
                        "name": "tag1",
                        "labelId": "12345678-1234-abcd-eeff-0123456789ab"
                    },
                    "222": {
                        "name": "testTag",
                        "labelId": "aabbccdd-eeff-1111-2222-123456123456"
                    }
                }
            }
        """.trimIndent()

    @Test
    fun `test real example 1`() {
        val expectedStructure = RecordsStructure(
            payloadType = PayloadType.JSON,
            nullable = false,
            headerFields = emptyHeaders,
            jsonFields = listOf(
                RecordField(
                    name = null,
                    fullName = null,
                    type = RecordFieldType.OBJECT,
                    children = listOf(
                        RecordField(
                            name = "accountId",
                            fullName = "accountId",
                            type = RecordFieldType.INTEGER
                        ),
                        RecordField(
                            name = "action",
                            fullName = "action",
                            type = RecordFieldType.STRING
                        ),
                        RecordField(
                            name = "personModifications",
                            fullName = "personModifications",
                            type = RecordFieldType.ARRAY,
                            children = listOf(
                                RecordField(
                                    name = null,
                                    fullName = "personModifications[*]",
                                    type = RecordFieldType.OBJECT,
                                    children = listOf(
                                        RecordField(
                                            name = "id",
                                            fullName = "personModifications[*].id",
                                            type = RecordFieldType.INTEGER
                                        ),
                                        RecordField(
                                            name = "firstName",
                                            fullName = "personModifications[*].firstName",
                                            type = RecordFieldType.STRING
                                        ),
                                        RecordField(
                                            name = "labels",
                                            fullName = "personModifications[*].labels",
                                            type = RecordFieldType.ARRAY,
                                            children = listOf(
                                                RecordField(
                                                    name = null,
                                                    fullName = "personModifications[*].labels[*]",
                                                    type = RecordFieldType.STRING
                                                )
                                            )
                                        ),
                                        RecordField(
                                            name = "officeLocations",
                                            fullName = "personModifications[*].officeLocations",
                                            type = RecordFieldType.ARRAY,
                                            children = listOf(
                                                RecordField(
                                                    name = null,
                                                    fullName = "personModifications[*].officeLocations[*]",
                                                    type = RecordFieldType.OBJECT,
                                                    children = listOf(
                                                        RecordField(
                                                            name = "id",
                                                            fullName = "personModifications[*].officeLocations[*].id",
                                                            type = RecordFieldType.INTEGER
                                                        ),
                                                        RecordField(
                                                            name = "type",
                                                            fullName = "personModifications[*].officeLocations[*].type",
                                                            type = RecordFieldType.STRING
                                                        ),
                                                        RecordField(
                                                            name = "headquarter",
                                                            fullName = "personModifications[*].officeLocations[*].headquarter",
                                                            type = RecordFieldType.BOOLEAN
                                                        ),
                                                    )
                                                ),
                                            )
                                        ),
                                        RecordField(
                                            name = "attributes",
                                            fullName = "personModifications[*].attributes",
                                            type = RecordFieldType.OBJECT,
                                            children = listOf(
                                                RecordField(
                                                    name = "d4dd1234-ccdd-1024-2048-112233445566",
                                                    fullName = "personModifications[*].attributes.d4dd1234-ccdd-1024-2048-112233445566",
                                                    type = RecordFieldType.STRING
                                                ),
                                            )
                                        ),
                                        RecordField(
                                            name = "modifiedAt",
                                            fullName = "personModifications[*].modifiedAt",
                                            type = RecordFieldType.INTEGER
                                        ),
                                        RecordField(
                                            name = "modifiedFrom",
                                            fullName = "personModifications[*].modifiedFrom",
                                            type = RecordFieldType.STRING
                                        ),
                                        RecordField(
                                            name = "systemAttributes",
                                            fullName = "personModifications[*].systemAttributes",
                                            type = RecordFieldType.OBJECT,
                                            children = listOf(
                                                RecordField(
                                                    name = "lastActive",
                                                    fullName = "personModifications[*].systemAttributes.lastActive",
                                                    type = RecordFieldType.INTEGER
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        RecordField(
                            name = "attributesMetadata",
                            fullName = "attributesMetadata",
                            type = RecordFieldType.OBJECT,
                            children = listOf(
                                RecordField(
                                    name = "d4dd1234-ccdd-1024-2048-112233445566",
                                    fullName = "attributesMetadata.d4dd1234-ccdd-1024-2048-112233445566",
                                    type = RecordFieldType.STRING
                                ),
                            )
                        ),
                        RecordField(
                            name = "labels",
                            fullName = "labels",
                            type = RecordFieldType.OBJECT,
                            children = listOf(
                                RecordField(
                                    name = "111",
                                    fullName = "labels.111",
                                    type = RecordFieldType.OBJECT,
                                    children = listOf(
                                        RecordField(
                                            name = "name",
                                            fullName = "labels.111.name",
                                            type = RecordFieldType.STRING
                                        ),
                                        RecordField(
                                            name = "labelId",
                                            fullName = "labels.111.labelId",
                                            type = RecordFieldType.STRING
                                        ),
                                    )
                                ),
                                RecordField(
                                    name = "222",
                                    fullName = "labels.222",
                                    type = RecordFieldType.OBJECT,
                                    children = listOf(
                                        RecordField(
                                            name = "name",
                                            fullName = "labels.222.name",
                                            type = RecordFieldType.STRING
                                        ),
                                        RecordField(
                                            name = "labelId",
                                            fullName = "labels.222.labelId",
                                            type = RecordFieldType.STRING
                                        ),
                                    )
                                ),
                            )
                        ),
                    ),
                )
            )
        )
        assertJsonStructure(expectedStructure, jsonRealisticExample)
    }

    @Test
    fun performanceTest() {
        val payload = jsonRealisticExample.encodeToByteArray()
        val recordKey = byteArrayOf(0x0)
        //warmup
        with(newAnalyzer(valueSamplingEnabled = true)) {
            repeat(20_000) {
                analyzeRecord(CLUSTER, ConsumerRecord(TOPIC, 0, 0, recordKey, payload))
            }
        }
        val times = 100_000
        val durationSec = measureTimeMillis {
            with(newAnalyzer(valueSamplingEnabled = true)) {
                repeat(times) {
                    analyzeRecord(CLUSTER, ConsumerRecord(TOPIC, 0, 0, recordKey, payload))
                }
            }
        }.div(1000.0)
        val speedMsgPerSec = times / durationSec
        println("analyze speed: $speedMsgPerSec msg/sec")
        assertThat(speedMsgPerSec).`as`("analyze ingestion msg/sec").isGreaterThan(10_000.0)
    }

    @Test
    fun `test json with variable keys`() {
        val json = """
            [
                {
                    "1": "v",
                    "2": "v",
                    "int": 123
                },
                {
                    "3": "v",
                    "4": "v"
                },
                {
                    "5": "v2"
                }
            ]
        """.trimIndent()
        val expectedStructure = RecordsStructure(
            payloadType = PayloadType.JSON,
            headerFields = emptyHeaders,
            jsonFields = listOf(
                RecordField(
                    name = null,
                    fullName = null,
                    type = RecordFieldType.ARRAY,
                    children = listOf(
                        RecordField(
                            name = null,
                            fullName = "[*]",
                            type = RecordFieldType.OBJECT,
                            children = listOf(
                                RecordField(
                                    name = null,
                                    fullName = "[*].*",
                                    type = RecordFieldType.STRING,
                                ),
                                RecordField(
                                    name = null,
                                    fullName = "[*].*",
                                    type = RecordFieldType.INTEGER,
                                ),
                            )
                        )
                    )
                )
            )
        )
        assertJsonStructure(expectedStructure, json)
    }


    @Test
    fun `test json with variable keys 2`() {
        val json1 = """
            {
                "labels": {
                    "111": {
                        "name": "tag1",
                        "labelId": "12345678-1234-abcd-eeff-0123456789ab"
                    },
                    "222": {
                        "name": "testTag",
                        "labelId": "aabbccdd-eeff-1111-2222-123456123456"
                    }
                }
            }
        """.trimIndent()
        val json2 = """
            {
                "labels": {
                    "444": {
                        "name": "tag4",
                        "labelId": "12345678-1234-abcd-eeff-0123456789ab"
                    }
                }
            }
        """.trimIndent()
        val expectedStructure = RecordsStructure(
            payloadType = PayloadType.JSON,
            headerFields = emptyHeaders,
            jsonFields = listOf(
                RecordField(
                    name = null,
                    fullName = null,
                    type = RecordFieldType.OBJECT,
                    children = listOf(
                        RecordField(
                            name = "labels",
                            fullName = "labels",
                            type = RecordFieldType.OBJECT,
                            children = listOf(
                                RecordField(
                                    name = null,
                                    fullName = "labels.*",
                                    type = RecordFieldType.OBJECT,
                                    children = listOf(
                                        RecordField(
                                            name = "name",
                                            fullName = "labels.*.name",
                                            type = RecordFieldType.STRING
                                        ),
                                        RecordField(
                                            name = "labelId",
                                            fullName = "labels.*.labelId",
                                            type = RecordFieldType.STRING
                                        ),
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
        assertJsonStructure(expectedStructure, json1, json2)
    }

    @Test
    fun `test json with absent fields`() {
        val json1 = """
            {
                "always": "val1",
                "sometimes": "val"
            }
        """.trimIndent()
        val json2 = """
            {
                "always": "val2"
            }
        """.trimIndent()
        val expectedStructure = RecordsStructure(
            payloadType = PayloadType.JSON,
            headerFields = emptyHeaders,
            jsonFields = listOf(
                RecordField(
                    name = null,
                    fullName = null,
                    type = RecordFieldType.OBJECT,
                    children = listOf(
                        RecordField(
                            name = "always",
                            fullName = "always",
                            type = RecordFieldType.STRING
                        ),
                        RecordField(
                            name = "sometimes",
                            fullName = "sometimes",
                            type = RecordFieldType.STRING,
                            nullable = true
                        ),
                    )
                )
            )
        )
        assertJsonStructure(expectedStructure, json1, json2)
    }

    @Test
    fun `test json with different fields of same type`() {
        val json1 = """ {"f1": "val1" }"""
        val json2 = """ {"f2": "val2" }"""
        val json3 = """ {"f3": "val3" }"""
        val expectedStructure = RecordsStructure(
            payloadType = PayloadType.JSON,
            headerFields = emptyHeaders,
            jsonFields = listOf(
                RecordField(
                    name = null,
                    fullName = null,
                    type = RecordFieldType.OBJECT,
                    children = listOf(
                        RecordField(
                            name = null,
                            fullName = "*",
                            type = RecordFieldType.STRING
                        )
                    )
                )
            )
        )
        assertJsonStructure(expectedStructure, json1, json2, json3)
    }

    @Test
    fun `test json with different and in-common fields of same type`() {
        val jsons = (1..22).zipWithNext()
            .map { (index1, index2) -> """ {"f$index1": "v", "f$index2": "v" }""" }

        val expectedStructure = RecordsStructure(
            payloadType = PayloadType.JSON,
            headerFields = emptyHeaders,
            jsonFields = listOf(
                RecordField(
                    name = null,
                    fullName = null,
                    type = RecordFieldType.OBJECT,
                    children = listOf(
                        RecordField(
                            name = null,
                            fullName = "*",
                            type = RecordFieldType.STRING,
                            nullable = true,
                        )
                    )
                )
            )
        )
        assertJsonStructure(expectedStructure, jsons = jsons.toTypedArray())
    }

    @Test
    fun `test json with different fields but start being constant`() {
        val json1 = """ {"f1": "val1" }"""
        val json2 = """ {"f2": "val2" }"""
        val json3 = """ {"const": "val3" }"""
        val expectedStructure = RecordsStructure(
            payloadType = PayloadType.JSON,
            headerFields = emptyHeaders,
            jsonFields = listOf(
                RecordField(
                    name = null,
                    fullName = null,
                    type = RecordFieldType.OBJECT,
                    children = listOf(
                        RecordField(
                            name = "const",
                            fullName = "const",
                            type = RecordFieldType.STRING
                        )
                    )
                )
            )
        )
        with(newAnalyzer(timeWindow = 200)) {
            acceptRecord(json1)
            acceptRecord(json2)
            acceptRecord(json3)
            repeat(4) {
                Thread.sleep(51)
                acceptRecord(json3)
            }
            structure().assertEqualsTo(expectedStructure)
        }
    }

    @Test
    fun `test json with different fields with different type`() {
        val json1 = """ {"f1": "val1" }"""
        val json2 = """ {"f2": 123456 }"""
        val expectedStructure = RecordsStructure(
            payloadType = PayloadType.JSON,
            headerFields = emptyHeaders,
            jsonFields = listOf(
                RecordField(
                    name = null,
                    fullName = null,
                    type = RecordFieldType.OBJECT,
                    children = listOf(
                        RecordField(
                            name = null,
                            fullName = "*",
                            type = RecordFieldType.STRING,
                        ),
                        RecordField(
                            name = null,
                            fullName = "*",
                            type = RecordFieldType.INTEGER,
                        ),
                    )
                )
            )
        )
        assertJsonStructure(expectedStructure, json1, json2)
    }

    @Test
    fun `test nullable decay with time`() {
        fun expectedStructure(nullable: Boolean) = RecordsStructure(
            payloadType = PayloadType.JSON,
            headerFields = emptyHeaders,
            jsonFields = listOf(
                RecordField(
                    name = null,
                    fullName = null,
                    type = RecordFieldType.OBJECT,
                    children = listOf(
                        RecordField(
                            "field",
                            "field",
                            RecordFieldType.STRING,
                            nullable = nullable
                        ),
                    )
                )
            )
        )
        with(newAnalyzer(timeWindow = 200)) {
            acceptRecord("""{ "field":"val1" }""")
            structure().assertEqualsTo(expectedStructure(false))
            acceptRecord("""{ }""")
            structure().assertEqualsTo(expectedStructure(true))
            acceptRecord("""{ "field":"val2" }""")
            structure().assertEqualsTo(expectedStructure(true))
            Thread.sleep(201)
            acceptRecord("""{ "field":"val3" }""")
            structure().assertEqualsTo(expectedStructure(false))
        }
    }

    @Test
    fun `test nullable record decay with time`() {
        with(newAnalyzer(timeWindow = 200)) {
            acceptRecord(null)
            assertThat(structure()).isEqualTo(
                RecordsStructure(
                    PayloadType.NULL,
                    nullable = true,
                    headerFields = emptyHeaders
                )
            )

            acceptRecord(""" "foo" """)
            structure().assertEqualsTo(
                RecordsStructure(
                    PayloadType.JSON,
                    nullable = true,
                    headerFields = emptyHeaders,
                    jsonFields = listOf(
                        RecordField(
                            name = null,
                            fullName = null,
                            RecordFieldType.STRING
                        )
                    )
                )
            )

            Thread.sleep(201)

            acceptRecord(""" "bar" """)
            structure().assertEqualsTo(
                RecordsStructure(
                    PayloadType.JSON,
                    nullable = false,
                    headerFields = emptyHeaders,
                    jsonFields = listOf(
                        RecordField(
                            name = null,
                            fullName = null,
                            RecordFieldType.STRING
                        )
                    )
                )
            )

            acceptRecord(null)
            structure().assertEqualsTo(
                RecordsStructure(
                    PayloadType.JSON,
                    nullable = true,
                    headerFields = emptyHeaders,
                    jsonFields = listOf(
                        RecordField(
                            name = null,
                            fullName = null,
                            RecordFieldType.STRING
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `test analyzing headers structure`() {
        with(newAnalyzer()) {
            acceptRecord(payload = null, mapOf("MY_HEADER" to "x", "OTHER_HEADER" to "y", "COUNT" to "123"))
            structure().assertEqualsTo(
                RecordsStructure(
                    PayloadType.NULL,
                    nullable = true,
                    headerFields = listOf(
                        RecordField(
                            name = null,
                            fullName = null,
                            RecordFieldType.OBJECT,
                            children = listOf(
                                RecordField(
                                    name = "MY_HEADER",
                                    fullName = "MY_HEADER",
                                    type = RecordFieldType.STRING,
                                ),
                                RecordField(
                                    name = "OTHER_HEADER",
                                    fullName = "OTHER_HEADER",
                                    type = RecordFieldType.STRING,
                                ),
                                RecordField(
                                    name = "COUNT",
                                    fullName = "COUNT",
                                    type = RecordFieldType.INTEGER,
                                ),
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `sample enumerated value`() {
        val json1 = """{"enum":"FOO"}"""
        val json2 = """{"enum":"BAR"}"""
        val json3 = """{"enum":"BAZ"}"""
        val json4 = """{"enum":"FOO"}"""
        val expected = RecordsStructure(
            PayloadType.JSON,
            headerFields = emptyHeaders,
            jsonFields = listOf(
                RecordField(
                    name = null,
                    fullName = null,
                    RecordFieldType.OBJECT,
                    children = listOf(
                        RecordField(
                            name = "enum",
                            fullName = "enum",
                            type = RecordFieldType.STRING,
                            value = com.infobip.kafkistry.model.RecordFieldValue(
                                highCardinality = false,
                                tooBig = false,
                                valueSet = setOf("FOO", "BAR", "BAZ")
                            )
                        ),
                    )
                )
            )
        )
        newAnalyzer(valueSamplingEnabled = true)
            .assertJsonStructure(expected, json1, json2, json3, json4)
    }

    @Test
    fun `sample high cardinality value`() {
        val jsons = arrayOf(
            """{"name":"a"}""",
            """{"name":"b"}""",
            """{"name":"c"}""",
            """{"name":"d"}""",
            """{"name":"e"}""",
            """{"name":"f"}""",
            """{"name":"g"}""",
        )
        val expected = RecordsStructure(
            PayloadType.JSON,
            headerFields = emptyHeaders,
            jsonFields = listOf(
                RecordField(
                    name = null,
                    fullName = null,
                    RecordFieldType.OBJECT,
                    children = listOf(
                        RecordField(
                            name = "name",
                            fullName = "name",
                            type = RecordFieldType.STRING,
                            value = com.infobip.kafkistry.model.RecordFieldValue(
                                highCardinality = true,
                                tooBig = false
                            ),
                        ),
                    )
                )
            )
        )
        newAnalyzer(valueSamplingEnabled = true, valSamplingMaxCardinality = 5)
            .assertJsonStructure(expected, *jsons)
    }

    @Test
    fun `merge null type with other type of same name`() {
        val jsons = arrayOf(
            """{"flag":true}""",
            """{"flag":false}""",
            """{"flag":null}""",
        )
        val expected = RecordsStructure(
            PayloadType.JSON,
            headerFields = emptyHeaders,
            jsonFields = listOf(
                RecordField(
                    name = null,
                    fullName = null,
                    RecordFieldType.OBJECT,
                    children = listOf(
                        RecordField(
                            name = "flag",
                            fullName = "flag",
                            type = RecordFieldType.BOOLEAN,
                            value = com.infobip.kafkistry.model.RecordFieldValue(
                                highCardinality = false,
                                tooBig = false,
                                valueSet = setOf(true, false)
                            ),
                            nullable = true,
                        ),
                    )
                )
            )
        )
        newAnalyzer(valueSamplingEnabled = true, valSamplingMaxCardinality = 5)
            .assertJsonStructure(expected, *jsons)
    }

    @Test
    fun `array of mixed  type`() {
        with(newAnalyzer()) {
            acceptRecord("""["x","y",true]""")
            structure().assertEqualsTo(
                RecordsStructure(
                    PayloadType.JSON,
                    headerFields = emptyHeaders,
                    jsonFields = listOf(
                        RecordField(
                            name = null,
                            fullName = null,
                            RecordFieldType.ARRAY,
                            children = listOf(
                                RecordField(
                                    name = null,
                                    fullName = "[*]",
                                    type = RecordFieldType.STRING,
                                ),
                                RecordField(
                                    name = null,
                                    fullName = "[*]",
                                    type = RecordFieldType.BOOLEAN,
                                ),
                            )
                        )
                    )
                )
            )
        }
    }


    @Test
    fun `squash dynamic keys - diversity`() {
        with(newAnalyzer()) {
            acceptRecord("""{"a":1}""")
            acceptRecord("""{"b":2}""")
            acceptRecord("""{"c":3}""")
            structure().assertEqualsTo(
                RecordsStructure(
                    PayloadType.JSON,
                    headerFields = emptyHeaders,
                    jsonFields = listOf(
                        RecordField(
                            name = null,
                            fullName = null,
                            RecordFieldType.OBJECT,
                            children = listOf(
                                RecordField(
                                    name = null,
                                    fullName = "*",
                                    type = RecordFieldType.INTEGER,
                                ),
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `squash dynamic keys - not java identifier name`() {
        with(newAnalyzer()) {
            acceptRecord("""{"1":1}""")
            Thread.sleep(1) //force different timestamp
            acceptRecord("""{"1":2}""")
            structure().assertEqualsTo(
                RecordsStructure(
                    PayloadType.JSON,
                    headerFields = emptyHeaders,
                    jsonFields = listOf(
                        RecordField(
                            name = null,
                            fullName = null,
                            RecordFieldType.OBJECT,
                            children = listOf(
                                RecordField(
                                    name = null,
                                    fullName = "*",
                                    type = RecordFieldType.INTEGER,
                                ),
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `dont squash dynamic keys - absent`() {
        with(newAnalyzer()) {
            acceptRecord("""{"a":1}""")
            acceptRecord("""{}""")
            structure().assertEqualsTo(
                RecordsStructure(
                    PayloadType.JSON,
                    headerFields = emptyHeaders,
                    jsonFields = listOf(
                        RecordField(
                            name = null,
                            fullName = null,
                            RecordFieldType.OBJECT,
                            children = listOf(
                                RecordField(
                                    name = "a",
                                    fullName = "a",
                                    type = RecordFieldType.INTEGER,
                                    nullable = true,
                                ),
                            )
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `dont squash dynamic keys - with minuses`() {
        with(newAnalyzer()) {
            acceptRecord("""{"x-y":1}""")
            acceptRecord("""{"x-y":1}""")
            structure().assertEqualsTo(
                RecordsStructure(
                    PayloadType.JSON,
                    headerFields = emptyHeaders,
                    jsonFields = listOf(
                        RecordField(
                            name = null,
                            fullName = null,
                            RecordFieldType.OBJECT,
                            children = listOf(
                                RecordField(
                                    name = "x-y",
                                    fullName = "x-y",
                                    type = RecordFieldType.INTEGER,
                                ),
                            )
                        )
                    )
                )
            )
        }
    }


}

