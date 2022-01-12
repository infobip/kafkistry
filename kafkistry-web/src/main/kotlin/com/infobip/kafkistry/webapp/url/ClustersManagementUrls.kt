package com.infobip.kafkistry.webapp.url

import com.infobip.kafkistry.model.KafkaClusterIdentifier

class ClustersManagementUrls(base: String) : BaseUrls() {

    companion object {
        const val CLUSTERS_MANAGEMENT = "/clusters/management"
        const val CLUSTERS_MANAGEMENT_APPLY_THROTTLING = "/apply-throttling"
    }
    private val showApplyThrottling = Url("$base$CLUSTERS_MANAGEMENT_APPLY_THROTTLING", listOf("clusterIdentifier"))

    fun showApplyThrottling(
            clusterIdentifier: KafkaClusterIdentifier
    ) = showApplyThrottling.render("clusterIdentifier" to clusterIdentifier)

}