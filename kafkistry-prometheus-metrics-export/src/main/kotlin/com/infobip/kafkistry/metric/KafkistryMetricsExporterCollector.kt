package com.infobip.kafkistry.metric

import com.infobip.kafkistry.metric.config.APP_METRICS_ENABLED_PROPERTY
import com.infobip.kafkistry.metric.config.PrometheusMetricsProperties
import com.infobip.kafkistry.service.acl.AclsInspectionService
import com.infobip.kafkistry.service.background.BackgroundJob
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import com.infobip.kafkistry.service.cluster.ClusterStatusProviderService
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.consumers.ConsumersService
import com.infobip.kafkistry.service.oldestrecordage.OldestRecordAgeService
import com.infobip.kafkistry.service.replicadirs.ReplicaDirsService
import com.infobip.kafkistry.service.topic.TopicsInspectionService
import com.infobip.kafkistry.service.topic.offsets.TopicOffsetsService
import io.prometheus.client.Collector
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.atomic.AtomicReference

@Component
@ConditionalOnProperty(APP_METRICS_ENABLED_PROPERTY, matchIfMissing = true)
class KafkistryMetricsExporterCollector(
    private val properties: PrometheusMetricsProperties,
    private val backgroundJobs: BackgroundJobIssuesRegistry,
    private val kafkistryCollectors: List<KafkistryMetricsCollector>,
    private val clusterStatusProviderService: ClusterStatusProviderService,
    private val clustersRegistryService: ClustersRegistryService,
    private val inspectionService: TopicsInspectionService,
    private val consumersService: ConsumersService,
    private val topicOffsetsService: TopicOffsetsService,
    private val oldestRecordAgeService: Optional<OldestRecordAgeService>,
    private val replicaDirsService: ReplicaDirsService,
    private val aclsInspectionService: AclsInspectionService,
) : Collector() {

    private val preCache = AtomicReference<List<MetricFamilySamples>>(emptyList())

    private val backgroundJob = BackgroundJob.of(
        category = "Metrics", phase = "refresh-cache", description = "Refresh pre-cached metrics",
    )

    override fun collect(): List<MetricFamilySamples> {
        return if (properties.preCached) {
            preCache.get()
        } else {
            doCollect()
        }
    }

    @Scheduled(fixedRateString = "#{prometheusMetricsProperties.preCacheRefreshMs()}")
    fun refreshCache() {
        if (!properties.preCached) {
            return
        }
        backgroundJobs.doCapturingException(backgroundJob) {
            preCache.set(doCollect())
        }
    }

    private fun doCollect(): List<MetricFamilySamples> {
        val context = MetricsDataContext(
            clusters = clustersRegistryService.listClustersRefs().associateBy { it.identifier },
            clusterStatuses = clusterStatusProviderService.statuses(),
            topicInspections = inspectionService.inspectAllTopics() + inspectionService.inspectUnknownTopics(),
            clustersGroups = consumersService.allConsumersData().clustersGroups,
            allClustersTopicsOffsets = topicOffsetsService.allClustersTopicsOffsets(),
            allClustersTopicOldestAges = oldestRecordAgeService.orElse(null)
                ?.allClustersTopicOldestRecordAges().orEmpty(),
            allClustersTopicReplicaInfos = replicaDirsService.allClustersTopicReplicaInfos(),
            aclPrincipalInspections = aclsInspectionService.inspectAllPrincipals() + aclsInspectionService.inspectUnknownPrincipals(),
        )
        return kafkistryCollectors.flatMap { it.expose(context) }
    }
}