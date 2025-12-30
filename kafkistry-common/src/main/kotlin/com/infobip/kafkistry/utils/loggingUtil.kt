package com.infobip.kafkistry.utils

import org.slf4j.MDC
import org.slf4j.event.Level

const val LOG_LEVEL_MARKER_KEY = "X-Log-Level"

inline fun <T> doWithNoLoggingOverrides(crossinline block: () -> T): T = doWithLogging(null, block)
inline fun <T> doWithOnlyErrorLogging(crossinline block: () -> T): T = doWithLogging(Level.ERROR, block)

inline fun <T> doWithLogging(level: Level?, crossinline block: () -> T): T {
    val prevLevel = MDC.get(LOG_LEVEL_MARKER_KEY)
    if (level == null) {
        MDC.remove(LOG_LEVEL_MARKER_KEY)
    } else {
        MDC.put(LOG_LEVEL_MARKER_KEY, level.name)
    }
    return try {
        block()
    } finally {
        if (prevLevel == null) {
            MDC.remove(LOG_LEVEL_MARKER_KEY)
        } else {
            MDC.put(LOG_LEVEL_MARKER_KEY, prevLevel)
        }
    }
}

/**
 * Sanitizes a string for safe logging by replacing control characters that could be used
 * for log injection attacks (newlines, carriage returns, tabs, ANSI escape sequences, etc.)
 * with their escaped representations.
 */
fun String?.sanitizeForLog(): String {
    if (this == null) return "null"
    return this.replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
        .replace("\u001B", "\\u001B") // ANSI escape sequence
        .replace("\u0000", "\\u0000") // null byte
}

/**
 * Sanitizes any object for safe logging by converting it to a string and sanitizing control characters.
 */
fun Any?.sanitizeForLog(): String {
    return this?.toString().sanitizeForLog()
}