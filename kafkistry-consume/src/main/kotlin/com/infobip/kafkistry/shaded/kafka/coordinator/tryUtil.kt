package com.infobip.kafkistry.shaded.kafka.coordinator

fun <T> tryParseOrNull(operation: () -> T): T? = try {
    operation()
} catch (_: IllegalStateException) {
    null
}