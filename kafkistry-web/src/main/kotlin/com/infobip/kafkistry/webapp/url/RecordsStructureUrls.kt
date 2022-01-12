package com.infobip.kafkistry.webapp.url

import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName

class RecordsStructureUrls(base: String) : BaseUrls() {

    companion object {
        const val RECORDS_STRUCTURE = "/records-structure"
        const val RECORDS_STRUCTURE_TOPIC = "/topic-structure"
        const val RECORDS_STRUCTURE_DRY_RUN = "/dry-run"
        const val RECORDS_STRUCTURE_DRY_RUN_INSPECT = "/dry-run-inspect"
    }

    private val showMenu = Url(base)
    private val showTopicStructure = Url("$base$RECORDS_STRUCTURE_TOPIC", listOf("topicName", "clusterIdentifier"))
    private val showDryRun = Url("$base$RECORDS_STRUCTURE_DRY_RUN")
    private val showDryRunInspect = Url("$base$RECORDS_STRUCTURE_DRY_RUN_INSPECT")

    fun showMenuPage() = showMenu.render()

    @JvmOverloads
    fun showTopicStructurePage(
            topicName: TopicName,
            clusterIdentifier: KafkaClusterIdentifier? = null
    ) = showTopicStructure.render(
        "topicName" to topicName,
        "clusterIdentifier" to clusterIdentifier
    )

    fun showDryRun() = showDryRun.render()
    fun showDryRunInspect() = showDryRunInspect.render()

}