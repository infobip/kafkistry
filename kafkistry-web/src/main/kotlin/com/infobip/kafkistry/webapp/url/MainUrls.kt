package com.infobip.kafkistry.webapp.url

class MainUrls(base: String): BaseUrls() {

    companion object {
        const val HOME = "/home"
        const val PENDING_REQUESTS = "/home/pending-requests"
        const val CLUSTER_STATS = "/home/clusters-stats"
        const val TAGS_STATS = "/home/tags-stats"
        const val TOPIC_STATS = "/home/topics-stats"
        const val CONSUMER_GROUPS_STATS = "/home/consumer-groups-stats"
        const val ACLS_STATS = "/home/acls-stats"
        const val QUOTAS_STATS = "/home/quotas-stats"
    }

    private val url = Url("$base$HOME")

    private val showPendingRequests = Url("$base$PENDING_REQUESTS")

    private val showClustersStats = Url("$base$CLUSTER_STATS")
    private val showTagsStats = Url("$base$TAGS_STATS")
    private val showTopicsStats = Url("$base$TOPIC_STATS")
    private val showConsumerGroupsStats = Url("$base$CONSUMER_GROUPS_STATS")
    private val showAclsStats = Url("$base$ACLS_STATS")
    private val showQuotasStats = Url("$base$QUOTAS_STATS")

    fun url() = url.render()

    fun showPendingRequests() = showPendingRequests.render()

    fun showClustersStats() = showClustersStats.render()
    fun showTagsStats() = showTagsStats.render()
    fun showTopicsStats() = showTopicsStats.render()
    fun showConsumerGroupsStats() = showConsumerGroupsStats.render()
    fun showAclsStats() = showAclsStats.render()
    fun showQuotasStats() = showQuotasStats.render()
}