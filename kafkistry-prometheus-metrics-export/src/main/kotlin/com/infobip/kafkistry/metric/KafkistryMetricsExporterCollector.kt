package com.infobip.kafkistry.metric

import com.infobip.kafkistry.service.acl.AclsInspectionService
import com.infobip.kafkistry.service.cluster.ClustersRegistryService
import com.infobip.kafkistry.service.consumers.ConsumersService
import com.infobip.kafkistry.service.oldestrecordage.OldestRecordAgeService
import com.infobip.kafkistry.service.replicadirs.ReplicaDirsService
import com.infobip.kafkistry.service.topic.TopicsInspectionService
import com.infobip.kafkistry.service.topic.offsets.TopicOffsetsService
import io.prometheus.client.Collector
import org.springframework.stereotype.Component
import java.util.*

@Component
class KafkistryMetricsExporterCollector(
    private val kafkistryCollectors: List<KafkistryMetricsCollector>,
    private val clustersRegistryService: ClustersRegistryService,
    private val inspectionService: TopicsInspectionService,
    private val consumersService: ConsumersService,
    private val topicOffsetsService: TopicOffsetsService,
    private val oldestRecordAgeService: Optional<OldestRecordAgeService>,
    private val replicaDirsService: ReplicaDirsService,
    private val aclsInspectionService: AclsInspectionService,
) : Collector() {

    override fun collect(): List<MetricFamilySamples> {
        val context = MetricsDataContext(
            clusters = clustersRegistryService.listClustersRefs().associateBy { it.identifier },
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