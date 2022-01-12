package com.infobip.kafkistry.webapp.url

import java.lang.IllegalArgumentException
import java.net.URLEncoder

data class Url(
        val path: String,
        val params: List<String> = emptyList()
) {

    companion object {
        private val pathVarRegex = Regex("""\{(\w+)}""")
    }

    fun render(vararg data: Pair<String, String?>): String = render(data.associate { it })

    private fun render(data: Map<String, String?>): String {
        val usedPathVariables = mutableListOf<String>()
        return path.replace(pathVarRegex) { match ->
            val key = match.groupValues[1]
            usedPathVariables.add(key)
            val value = data[key] ?: throw IllegalArgumentException("Missing url path variable: '$key'")
            value.encodeUri()
        }.let { url ->
            val paramsUrl = data
                    .nonNullValues()
                    .filterKeys { it !in usedPathVariables }.map { (key, value) ->
                        if (key !in params) {
                            throw IllegalArgumentException("Unknown/unexpected data parameter '$key'")
                        }
                        "$key=" + value.encodeUri()
                    }
                    .joinToString(separator = "&")
                    .let {
                        when (it.isNotEmpty()) {
                            true -> "?$it"
                            false -> it
                        }
                    }
            url + paramsUrl
        }
    }

    private fun <K, V> Map<K, V?>.nonNullValues(): Map<K, V> {
        return this.mapNotNull { (k, v) -> v?.let { k to v } }.associate { it }
    }

    fun String.encodeUri(): String = URLEncoder.encode(this, "UTF-8")

}