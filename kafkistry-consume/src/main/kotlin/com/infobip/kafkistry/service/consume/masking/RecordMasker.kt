package com.infobip.kafkistry.service.consume.masking


interface RecordMasker {

    fun masksKey(): Boolean
    fun masksValue(): Boolean
    fun masksHeader(): Boolean

    fun maskKey(key: Any?): Any?
    fun maskValue(value: Any?): Any?
    fun maskHeader(name: String, header: Any?): Any?

    companion object NOOP : RecordMasker {

        override fun masksKey(): Boolean = false
        override fun masksValue(): Boolean = false
        override fun masksHeader(): Boolean = false

        override fun maskKey(key: Any?) = key
        override fun maskValue(value: Any?) = value
        override fun maskHeader(name: String, header: Any?) = header
    }
}

