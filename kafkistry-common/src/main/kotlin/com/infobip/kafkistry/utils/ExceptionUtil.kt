package com.infobip.kafkistry.utils

fun Throwable.deepToString(): String = deepToString(100)

private fun Throwable.deepToString(remainingDepth: Int): String {
    val cause = this.cause
    val message = when (message) {
        cause.toString() -> null
        else -> message
    }
    val thisString = javaClass.name + if (message != null) ": $message" else ""
    return thisString + when (cause != null && remainingDepth > 0) {
        true -> "; caused by: " + cause.deepToString(remainingDepth - 1)
        false -> ""
    }
}