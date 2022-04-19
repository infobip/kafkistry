package com.infobip.kafkistry.webapp.hostname

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.util.*

@Component
@ConfigurationProperties("app.hostname")
class HostnameProperties {
    var property = ""
    var value = ""
}

@Component
class HostnameResolver(
    private val properties: HostnameProperties,
) {

    val hostname: String = resolveHostname()

    private fun resolveHostname(): String {
        val os = System.getProperty("os.name").lowercase(Locale.getDefault())
        return when {
            properties.value.isNotBlank() -> properties.value
            properties.property.isNotBlank() -> {
                System.getProperty(properties.property) ?: System.getenv(properties.property)
            }
            "win" in os -> {
                System.getenv("COMPUTERNAME") ?: execHostname() ?: localhostHostname()
            }
            listOf("nix", "nux", "mac os x").any { it in os } -> {
                System.getenv("HOSTNAME") ?: execHostname() ?: localhostHostname()
            }
            else -> null
        }?.replaceCRLF() ?: "[unknown]"
    }

    private fun execHostname(): String? {
        return try {
            Runtime.getRuntime().exec(arrayOf("hostname"))
                .inputStream.reader(Charsets.UTF_8)
                .readText()
                .trim()
                .takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun localhostHostname(): String? {
        return try {
            InetAddress.getLocalHost().hostName?.takeIf { it != "localhost" }
        } catch (_: Exception) {
            null
        }
    }

    private fun String.replaceCRLF() = replace('\n', ' ').replace('\r', ' ')
}
