package com.infobip.kafkistry.model

enum class PayloadType {
    UNKNOWN, NULL, JSON;
}

enum class RecordFieldType {
    NULL,
    OBJECT,
    ARRAY,
    STRING,
    BOOLEAN,
    INTEGER,
    DECIMAL,
    DATE,
    BYTES;
}

/**
 *  @param payloadType: record payload type. Both binary and not-a-json text are labeled as an UNKNOWN payload
 *  @param jsonFields: list of {@link RecordField}
 *  @param nullable: nullable flag. Indicates that empty record with no fields has been seen
 */
data class RecordsStructure(
    val payloadType: PayloadType,
    val headerFields: List<RecordField>? = null,
    val jsonFields: List<RecordField>? = null,
    val nullable: Boolean = false,
)

/**
 *  @param name: record field name. Note, arrays or single values don't have name field set
 *  @param type: record field type {@RecordFieldType}
 *  @param children: list of record childrens
 *  @param nullable: nullable flag. Indicates that empty recordField has been seen
 */
data class RecordField (
    val name: String?,
    val fullName: String?,
    val type: RecordFieldType,
    val children: List<RecordField>? = null,
    val nullable: Boolean = false,
    val value: RecordFieldValue? = null,
)

data class RecordFieldValue(
    val highCardinality: Boolean,
    val tooBig: Boolean,
    val valueSet: Set<Any>? = null,
)

