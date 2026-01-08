package com.infobip.kafkistry.service.scrapingstatus

import com.infobip.kafkistry.kafkastate.AbstractKafkaStateProvider
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.KafkaClusterIdentifier
import org.springframework.stereotype.Service

data class ClusterScrapingStatus(
    val scraperClass: String,
    val stateType: StateType,
    val clusterIdentifier: KafkaClusterIdentifier,
    val stateTypeName: String,
    val lastRefreshTime: Long,
    val kafkistryInstance: String,
)

@Service
class ScrapingStatusService(
    private val kafkaStateProviders: List<AbstractKafkaStateProvider<*>>,
) {

    fun currentScrapingStatuses(): List<ClusterScrapingStatus> {
        return kafkaStateProviders
            .flatMap { provider ->
                provider.listAllLatestStates().map {
                    ClusterScrapingStatus(
                        provider.javaClass.name, it.stateType, it.clusterIdentifier, it.stateTypeName,
                        it.lastRefreshTime, it.kafkistryInstance,
                    )
                }
            }
    }
}