package com.infobip.kafkistry.service.cluster.inspect

import com.infobip.kafkistry.model.KafkaClusterIdentifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app.clusters-inspect")
class ClusterIssuesInspectorProperties {
    var excludedCheckerClasses: List<Class<out ClusterIssueChecker>> = emptyList()
}

@Component
class ClusterIssuesInspectorService(
    private val properties: ClusterIssuesInspectorProperties,
    private val issueCheckers: List<ClusterIssueChecker>,
) {

    fun inspectClusterIssues(clusterIdentifier: KafkaClusterIdentifier): List<ClusterInspectIssue> {
        return issueCheckers
            .filter { it.javaClass !in properties.excludedCheckerClasses }
            .flatMap { it.checkIssues(clusterIdentifier) }
            .sortedByDescending { it.level }
    }
}