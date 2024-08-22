package com.infobip.kafkistry.service.cluster

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

@Service
class ClusterStatusProviderService(
    private val clusterStatusService: ClusterStatusService,
    private val clusterIssuesInspectorService: ClusterIssuesInspectorService,
    private val backgroundJobIssuesRegistry: BackgroundJobIssuesRegistry,
) {

    private val precomputed = AtomicReference<List<ClusterStatusIssues>>(emptyList())
    private val backgroundJob = BackgroundJob.of(
        category = "status", description = "Re-compute cluster statuses and issues",
    )

    init {
        doRefreshIssues()
    }

    @Scheduled(fixedRateString = "#{poolingProperties.intervalMs()}")
    fun refresh() = doRefreshIssues()

    fun statuses(): List<ClusterStatusIssues> = precomputed.get()

    private fun doRefreshIssues() {
        backgroundJobIssuesRegistry.doCapturingException(backgroundJob) {
            precomputed.set(computeStatuses())
        }
    }

    private fun computeStatuses(): List<ClusterStatusIssues> {
        return clusterStatusService.clustersState()
            .map { state ->
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
}