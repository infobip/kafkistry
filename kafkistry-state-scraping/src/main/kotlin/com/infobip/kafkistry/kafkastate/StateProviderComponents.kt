package com.infobip.kafkistry.kafkastate

import com.infobip.kafkistry.hostname.HostnameResolver
import com.infobip.kafkistry.kafkastate.config.PoolingProperties
import com.infobip.kafkistry.kafkastate.coordination.StateDataPublisher
import com.infobip.kafkistry.kafkastate.coordination.StateScrapingCoordinator
import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import com.infobip.kafkistry.repository.KafkaClustersRepository
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import org.springframework.stereotype.Component

@Component
class StateProviderComponents(
    val clustersRepository: KafkaClustersRepository,
    val clusterFilter: ClusterEnabledFilter,
    val promProperties: PrometheusMetricsProperties,
    val poolingProperties: PoolingProperties,
    val scrapingCoordinator: StateScrapingCoordinator,
    val issuesRegistry: BackgroundJobIssuesRegistry,
    val stateDataPublisher: StateDataPublisher,
    val hostnameResolver: HostnameResolver,
) //avoids long argument list on every *StateProvider passed to its base class
