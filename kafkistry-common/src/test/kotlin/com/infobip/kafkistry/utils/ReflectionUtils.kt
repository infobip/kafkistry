package com.infobip.kafkistry.utils

fun <T> Any.getFieldReflective(fieldName: String): T {
    val field = javaClass.getDeclaredField(fieldName)
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return field.get(this) as T
}