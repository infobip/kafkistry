package com.infobip.kafkistry.autopilot.repository

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.infobip.kafkistry.autopilot.binding.AutopilotActionIdentifier
import com.infobip.kafkistry.autopilot.reporting.ActionOutcome
import org.slf4j.LoggerFactory
import java.io.File

class DiskPersistedActionsRepository(
    private val delegate: ActionsRepository,
    storageDir: String,
) : ActionsRepository {

    private val log = LoggerFactory.getLogger(DiskPersistedActionsRepository::class.java)
    private val mapper = jacksonObjectMapper().apply {
        enable(SerializationFeature.INDENT_OUTPUT)
    }

    private val dir = File(storageDir)
    private val storageFile = File(dir.absolutePath, "autopilotActions.json")

    init {
        if (!dir.exists()) {
            dir.mkdirs()
            log.info("Created missing dir for autopilot actions storage, path: '{}'", dir.absolutePath)
        } else if (storageFile.exists()) {
            val actionFlows = try {
                mapper.readValue(storageFile, object : TypeReference<List<ActionFlow>>() {}).also {
                    log.info("Loaded {} acton flows from '{}'", it.size, storageFile.absolutePath)
                }
            } catch (ex: Exception) {
                log.warn("Failed to load action flows from '{}'", storageFile.absolutePath, ex)
                emptyList()
            }
            delegate.putAll(actionFlows)
        } else {
            log.info("Didn't found action flows file to load from '{}'", storageFile.absolutePath)
        }
    }

    private fun persistAll() {
        try {
            mapper.writeValue(storageFile, findAll())
        } catch (ex: Exception) {
            log.error("Failed to store action flows into '{}'", storageFile.absolutePath, ex)
        }
    }

    override fun save(actionOutcome: ActionOutcome) {
        delegate.save(actionOutcome)
        persistAll()
    }

    override fun putAll(actionFlows: List<ActionFlow>) {
        delegate.putAll(actionFlows)
        persistAll()
    }

    override fun findAll(): List<ActionFlow> {
        return delegate.findAll()
    }

    override fun find(actionIdentifier: AutopilotActionIdentifier): ActionFlow? {
        return delegate.find(actionIdentifier)
    }

    override fun cleanup() {
        delegate.cleanup()
        persistAll()
    }

}