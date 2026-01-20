package com.infobip.kafkistry.api

import com.infobip.kafkistry.utils.deepToString
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.env.Environment
import org.springframework.core.env.PropertySource
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class EnvironmentPropertiesDto(
    val activeProfiles: List<String>,
    val propertySources: List<PropertySourceDto>,
    val allProperties: List<PropertyEntryDto>
)

data class PropertySourceDto(
    val name: String,
    val type: String,
    val precedenceOrder: Int,
    val properties: List<PropertyEntryDto>
)

data class PropertyEntryDto(
    val key: String,
    val value: String?,
    val resolvedValue: String?,
    val sensitive: Boolean,
    val origin: String? = null,
    val error: String? = null,
)

@RestController
@RequestMapping("\${app.http.root-path}/api/environment-properties")
class EnvironmentPropertiesApi(
    private val environment: Environment,
) {

    private val sensitivePatterns = listOf(
        "password", "passwd", "pwd",
        "secret",
        "token",
        "key", "apikey", "api.key", "api_key",
        "auth",
        "credential",
        "private",
        "confidential",
        "jdbc.password",
        "datasource.password",
        "jwt",
        "bearer",
        "oauth",
    )

    @GetMapping
    fun environmentProperties(): EnvironmentPropertiesDto {
        val configurableEnv = environment as? ConfigurableEnvironment
            ?: throw IllegalStateException("Environment is not configurable")

        val propertySources = mutableListOf<PropertySourceDto>()
        val allPropertiesMap = mutableMapOf<String, PropertyEntryDto>()
        var order = 0

        // Iterate through property sources in precedence order
        for (propertySource in configurableEnv.propertySources) {
            val properties = extractProperties(propertySource)
            if (properties.isNotEmpty()) {
                propertySources.add(
                    PropertySourceDto(
                        name = propertySource.name,
                        type = propertySource.determineSourceType(),
                        precedenceOrder = order,
                        properties = properties.sortedBy { it.key }
                    )
                )
                // Add to all properties map (earlier sources take precedence)
                properties.forEach { prop ->
                    if (!allPropertiesMap.containsKey(prop.key)) {
                        allPropertiesMap[prop.key] = prop.copy(origin = propertySource.name)
                    }
                }
                order++
            }
        }

        val allProperties = allPropertiesMap.values.sortedBy { it.key }
        val activeProfiles = environment.activeProfiles.toList()

        return EnvironmentPropertiesDto(
            activeProfiles = activeProfiles,
            propertySources = propertySources,
            allProperties = allProperties
        )
    }

    private fun extractProperties(propertySource: PropertySource<*>): List<PropertyEntryDto> {
        val properties = mutableListOf<PropertyEntryDto>()

        when (propertySource) {
            is EnumerablePropertySource<*> -> {
                for (propertyName in propertySource.propertyNames) {
                    val rawValue = propertySource.getProperty(propertyName)?.toString()
                    val propertyDto = try {
                        val resolvedValue = environment.getProperty(propertyName)
                        PropertyEntryDto(
                            key = propertyName,
                            value = rawValue,
                            resolvedValue = resolvedValue,
                            sensitive = propertyName.isSensitive(),
                        )
                    } catch (ex: Exception) {
                        PropertyEntryDto(
                            key = propertyName,
                            value = rawValue,
                            resolvedValue = null,
                            sensitive = propertyName.isSensitive(),
                            error = ex.deepToString(),
                        )
                    }
                    properties.add(propertyDto)
                }
            }
            else -> {
                // Non-enumerable property sources (like systemEnvironment with restrictions)
                // We'll skip these or handle them specially
            }
        }

        return properties
    }

    private fun String.isSensitive(): Boolean {
        val lowerKey = lowercase()
        return sensitivePatterns.any { lowerKey.contains(it) }
    }

    private fun PropertySource<*>.determineSourceType(): String = when {
        name.contains("commandLineArgs", ignoreCase = true) -> "COMMAND_LINE"
        name.contains("systemProperties", ignoreCase = true) -> "SYSTEM_PROPERTY"
        name.contains("systemEnvironment", ignoreCase = true) -> "ENVIRONMENT_VARIABLE"
        name.contains("application.yml", ignoreCase = true) ||
        name.contains("application.yaml", ignoreCase = true) -> "APPLICATION_YAML"
        name.contains("application.properties", ignoreCase = true) -> "APPLICATION_PROPERTIES"
        name.contains("application-", ignoreCase = true) -> "PROFILE_SPECIFIC"
        else -> "OTHER"
    }
}
