package com.infobip.kafkistry.webapp.url

import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName

class CompareUrls(base: String) : BaseUrls() {

    companion object {
        const val COMPARE = "/compare"
        const val COMPARE_RESULT = "/result"
    }

    private val showCompare = Url(base, listOf("topicName", "clusterIdentifier", "compareType"))
    private val showCompareResult = Url("$base$COMPARE_RESULT")

    @JvmOverloads
    fun showComparePage(
        topicName: TopicName? = null,
        clusterIdentifier: KafkaClusterIdentifier? = null,
        comparingSubjectType: String? = null
    ) = showCompare.render(
            "topicName" to topicName,
            "clusterIdentifier" to clusterIdentifier,
            "compareType" to comparingSubjectType
    )

    fun showCompareResult() = showCompareResult.render()

}