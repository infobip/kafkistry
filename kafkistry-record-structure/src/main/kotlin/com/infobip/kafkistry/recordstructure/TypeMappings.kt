package com.infobip.kafkistry.recordstructure

import com.infobip.kafkistry.model.RecordFieldType
import java.math.BigDecimal
import java.math.BigInteger
import java.time.*
import java.util.*

/**
 * Mapping of builtin Java types to their matching RecordField primitive type
 */
enum class TypeMappings(
    private val clazz: Class<*>?,
    private val fieldType: RecordFieldType
) {
    //byte array
    BYTES(ByteArray::class.java, RecordFieldType.BYTES),

    // Integer types
    PRIMITIVE_BYTE(Byte::class.javaPrimitiveType, RecordFieldType.INTEGER),
    PRIMITIVE_SHORT(Short::class.javaPrimitiveType, RecordFieldType.INTEGER),
    PRIMITIVE_INTEGER(Int::class.javaPrimitiveType, RecordFieldType.INTEGER),
    PRIMITIVE_LONG(Long::class.javaPrimitiveType, RecordFieldType.INTEGER),
    BYTE(java.lang.Byte::class.java, RecordFieldType.INTEGER),
    SHORT(java.lang.Short::class.java, RecordFieldType.INTEGER),
    INTEGER(java.lang.Integer::class.java, RecordFieldType.INTEGER),
    LONG(java.lang.Long::class.java, RecordFieldType.INTEGER),
    BIGINTEGER(BigInteger::class.java, RecordFieldType.INTEGER),

    // Decimal types
    PRIMITIVE_FLOAT(Float::class.javaPrimitiveType, RecordFieldType.DECIMAL),
    PRIMITIVE_DOUBLE(Double::class.javaPrimitiveType, RecordFieldType.DECIMAL),
    FLOAT(java.lang.Float::class.java, RecordFieldType.DECIMAL),
    DOUBLE(java.lang.Double::class.java, RecordFieldType.DECIMAL),
    BIGDECIMAL(BigDecimal::class.java, RecordFieldType.DECIMAL),

    // Boolean types
    PRIMITIVE_BOOLEAN(Boolean::class.javaPrimitiveType, RecordFieldType.BOOLEAN),
    BOOLEAN(java.lang.Boolean::class.java, RecordFieldType.BOOLEAN),

    // String types
    PRIMITIVE_CHAR(Char::class.javaPrimitiveType, RecordFieldType.STRING),
    CHAR(java.lang.Character::class.java, RecordFieldType.STRING),
    CHARSEQUENCE(CharSequence::class.java, RecordFieldType.STRING),
    STRING(String::class.java, RecordFieldType.STRING),
    UUID(java.util.UUID::class.java, RecordFieldType.STRING),

    // Date types
    ZONEDDATETIME(ZonedDateTime::class.java, RecordFieldType.DATE),
    DATE(Date::class.java, RecordFieldType.DATE),
    LOCALDATE(LocalDate::class.java, RecordFieldType.DATE),
    LOCALTIME(LocalTime::class.java, RecordFieldType.DATE),
    LOCALDATETIME(LocalDateTime::class.java, RecordFieldType.DATE),
    INSTANT(Instant::class.java, RecordFieldType.DATE),
    ZONE_ID(ZoneId::class.java, RecordFieldType.DATE),
    OFFSETDATETIME(OffsetDateTime::class.java, RecordFieldType.DATE);

    companion object {
        // Class objects are all singletons, so we can use that
        private val MAPPINGS: Map<Class<*>?, RecordFieldType> = values().associate { it.clazz to it.fieldType }

        /**
         * Return a primitive type for a given class, if any
         *
         * @param type the class
         * @return the primitive type if found, `RecordFieldType.NULL` otherwise
         */
        fun forClass(type: Class<*>): RecordFieldType = when {
            AbstractCollection::class.java.isAssignableFrom(type) -> RecordFieldType.ARRAY
            else -> MAPPINGS[type]
                ?: RecordFieldType.NULL
        }
    }
}