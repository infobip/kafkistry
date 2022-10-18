package com.infobip.kafkistry.service

interface NamedType {
    val name: String
    val level: StatusLevel
    val valid: Boolean
    val doc: String
}

enum class StatusLevel {
    SUCCESS,
    IMPORTANT,
    IGNORE,
    INFO,
    WARNING,
    ERROR,
    CRITICAL,
}

