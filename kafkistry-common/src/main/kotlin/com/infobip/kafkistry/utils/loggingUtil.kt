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