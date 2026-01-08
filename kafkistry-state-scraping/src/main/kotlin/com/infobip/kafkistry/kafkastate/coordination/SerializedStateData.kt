package com.infobip.kafkistry.kafkastate.coordination

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.infobip.kafkistry.kafkastate.StateData
import com.infobip.kafkistry.kafkastate.StateType
import java.io.Serializable

/**
 * Serializable wrapper for StateData that enables sharing across Hazelcast instances.
 *
 * Uses JSON serialization for the generic value type to avoid requiring all state classes
 * to implement Serializable and to provide better portability and debuggability.
 */
data class SerializedStateData(
    val kafkistryInstance: String,
    val stateType: StateType,
    val clusterIdentifier: String,
    val stateTypeName: String,
    val lastRefreshTime: Long,
    val valueType: String,  // Fully qualified class name for deserialization
    val valueJson: String?,
) : Serializable {

    companion object {
        private val objectMapper = jacksonObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

        /**
         * Convert StateData to SerializedStateData for Hazelcast storage.
         */
        fun <V> from(kafkistryInstance: String, stateData: StateData<V>): SerializedStateData {
            val valueJson = stateData.valueOrNull()?.let {
                objectMapper.writeValueAsString(it)
            }
            val valueType = stateData.valueOrNull()?.javaClass?.name ?: ""

            return SerializedStateData(
                kafkistryInstance = kafkistryInstance,
                stateType = stateData.stateType,
                clusterIdentifier = stateData.clusterIdentifier,
                stateTypeName = stateData.stateTypeName,
                lastRefreshTime = stateData.lastRefreshTime,
                valueType = valueType,
                valueJson = valueJson,
            )
        }
    }

    /**
     * Convert back to StateData by deserializing the JSON value.
     */
    fun <V> toStateData(): StateData<V> {
        val value = if (valueJson != null) {
            val clazz = Class.forName(valueType)
            @Suppress("UNCHECKED_CAST")
            objectMapper.readValue(valueJson, clazz) as V
        } else {
            null
        }

        return StateData(
            stateType = stateType,
            clusterIdentifier = clusterIdentifier,
            stateTypeName = stateTypeName,
            lastRefreshTime = lastRefreshTime,
            value = value,
        )
    }
}
