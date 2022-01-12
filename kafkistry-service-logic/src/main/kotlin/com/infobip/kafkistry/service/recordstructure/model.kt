package com.infobip.kafkistry.service.recordstructure

data class AnalyzeRecords(
    val payloadEncoding: PayloadEncoding,
    val records: List<AnalyzeRecord>,
)

data class AnalyzeRecord(
    val payload: String?,
    val headers: Map<String, String?>,
)

enum class PayloadEncoding {
    UTF8_STRING, BASE64
}
