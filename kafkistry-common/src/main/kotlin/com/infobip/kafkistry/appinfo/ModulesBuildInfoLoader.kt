package com.infobip.kafkistry.appinfo

import tools.jackson.databind.DeserializationFeature
import tools.jackson.dataformat.javaprop.JavaPropsMapper
import org.slf4j.LoggerFactory
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.io.support.ResourcePatternResolver
import org.springframework.stereotype.Component
import tools.jackson.module.kotlin.KotlinModule

private const val RESOURCES_PATTERN = "classpath*:*.git.properties"

@Component
class ModulesBuildInfoLoader {

    private val log = LoggerFactory.getLogger(ModulesBuildInfoLoader::class.java)

    private val modulesInfos = loadAll()

    fun modulesInfos(): List<ModuleBuildInfo> = modulesInfos

    private fun loadAll(): List<ModuleBuildInfo> {
        val mapper = JavaPropsMapper.builder().apply {
            addModule(KotlinModule.Builder().build())
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }.build()
        val patternResolver: ResourcePatternResolver = PathMatchingResourcePatternResolver()
        log.info("Finding modules build infos with pattern: '{}'", RESOURCES_PATTERN)
        val propertiesResources = patternResolver.getResources(RESOURCES_PATTERN)
        log.info("Found {} resources with pattern: '{}'", propertiesResources.size, RESOURCES_PATTERN)
        return propertiesResources.mapNotNull { resource ->
            log.info("Loading module build info from: {}", resource)
            try {
                mapper.readValue(resource.inputStream, ModuleBuildInfo::class.java).also {
                    log.info("Loaded module build info from '{}' {}", resource, it)
                }
            } catch (ex: Exception) {
                log.warn("Failed to load module build info from '{}'", resource, ex)
                null
            }
        }.sortedByDescending { it.git.build.time }
    }
}
