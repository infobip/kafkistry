package com.infobip.kafkistry.webapp.url

abstract class BaseUrls {

    fun schema(): Map<String, Url> {
        return this.javaClass.declaredFields
                .filter { it.type == Url::class.java }
                .associate { field ->
                    field.isAccessible = true
                    field.name to field.get(this) as Url
                }
    }
}
