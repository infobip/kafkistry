package com.infobip.kafkistry.service.cluster

import com.infobip.kafkistry.kafka.ClusterInfo
import com.infobip.kafkistry.kafkastate.StateType
import com.infobip.kafkistry.model.ClusterRef
import com.infobip.kafkistry.service.background.BackgroundJob
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import com.infobip.kafkistry.service.cluster.inspect.ClusterInspectIssue
import com.infobip.kafkistry.service.cluster.inspect.ClusterIssuesInspectorService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicReference

data class ClusterStatusIssues(
    val clusterRef: ClusterRef,
    val stateType: StateType,
    val issues: List<ClusterInspectIssue>,
)

data class ClusterStatusInfo(
    val clusterRef: ClusterRef,
    val statusInfo: ClusterInfo?,
)

@Service
class ClusterStatusProviderService(
    private val clusterStatusService: ClusterStatusService,
    private val clusterIssuesInspectorService: ClusterIssuesInspectorService,
    private val backgroundJobIssuesRegistry: BackgroundJobIssuesRegistry,
) {

    private val precomputedIssues = AtomicReference<List<ClusterStatusIssues>>(emptyList())
    private val precomputedInfos = AtomicReference<List<ClusterStatusInfo>>(emptyList())
    private val backgroundJob = BackgroundJob.of(
        category = "status", description = "Re-compute cluster statuses and issues",
    )

    init {
        doRefreshIssues()
    }

    @Scheduled(fixedRateString = "#{poolingProperties.intervalMs()}")
    fun refresh() = doRefreshIssues()

    fun statusIssues(): List<ClusterStatusIssues> = precomputedIssues.get()
    fun statusInfos(): List<ClusterStatusInfo> = precomputedInfos.get()

    private fun doRefreshIssues() {
        backgroundJobIssuesRegistry.doCapturingException(backgroundJob) {
            val clustersState = clusterStatusService.clustersState()
            precomputedIssues.set(clustersState.issues())
            precomputedInfos.set(clustersState.infos())
        }
    }

    private fun List<ClusterStatus>.issues(): List<ClusterStatusIssues> {
        return map { state ->
            val clusterIssues = if (state.clusterState == StateType.VISIBLE) {
                try {
                    clusterIssuesInspectorService.inspectClusterIssues(state.cluster.identifier)
                } catch (ex: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            ClusterStatusIssues(
                clusterRef = state.cluster.ref(),
                stateType = state.clusterState,
                issues = clusterIssues,
            )
        }
    }

    private fun List<ClusterStatus>.infos(): List<ClusterStatusInfo> {
        return map {
            ClusterStatusInfo(
                clusterRef = it.cluster.ref(),
                statusInfo = it.clusterInfo,
            )
        }
    }

}