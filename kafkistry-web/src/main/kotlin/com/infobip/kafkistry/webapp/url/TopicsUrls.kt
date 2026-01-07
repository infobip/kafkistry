package com.infobip.kafkistry.webapp.url

import com.infobip.kafkistry.model.KafkaClusterIdentifier
import com.infobip.kafkistry.model.TopicName

class TopicsUrls(base: String) : BaseUrls() {

    companion object {
        const val TOPICS = "/topics"
        const val TOPICS_ALL_TABLE = "/all-table"
        const val TOPICS_IMPORT = "/import"
        const val TOPICS_EDIT = "/edit"
        const val TOPICS_EDIT_ON_BRANCH = "/edit-on-branch"
        const val TOPICS_SUGGEST_EDIT = "/suggest-edit"
        const val TOPICS_FIX_VIOLATIONS_EDIT = "/fix-violations-edit"
        const val TOPICS_CLONE_ADD_NEW = "/clone-add-new"
        const val TOPICS_DELETE = "/delete"
        const val TOPICS_INSPECT = "/inspect"
        const val TOPICS_INSPECT_HISTORY = "/inspect/history"
        const val TOPICS_CLUSTER_INSPECT = "/cluster-inspect"
        const val TOPICS_DRY_RUN_INSPECT = "/dry-run-inspect"
        const val TOPICS_CREATE = "/create"
        const val TOPICS_WIZARD = "/wizard"
        const val TOPICS_WIZARD_CREATE = "/wizard/create"
    }

    private val showTopics = Url(base)
    private val showAllTopicsTable = Url("$base$TOPICS_ALL_TABLE")
    private val showImportTopic = Url("$base$TOPICS_IMPORT", listOf("topicName"))
    private val showEditTopic = Url("$base$TOPICS_EDIT", listOf("topicName"))
    private val showEditTopicOnBranch = Url("$base$TOPICS_EDIT_ON_BRANCH", listOf("topicName", "branch"))
    private val showSuggestEditTopic = Url("$base$TOPICS_SUGGEST_EDIT", listOf("topicName"))
    private val showFixViolationsEdit = Url("$base$TOPICS_FIX_VIOLATIONS_EDIT", listOf("topicName"))
    private val showCloneAddNewTopic = Url("$base$TOPICS_CLONE_ADD_NEW", listOf("topicName"))
    private val showDeleteTopic = Url("$base$TOPICS_DELETE", listOf("topicName"))
    private val showTopic = Url("$base$TOPICS_INSPECT", listOf("topicName"))
    private val showTopicHistory = Url("$base$TOPICS_INSPECT_HISTORY", listOf("topicName"))
    private val showInspectTopicOnCluster = Url("$base$TOPICS_CLUSTER_INSPECT", listOf("topicName", "clusterIdentifier"))
    private val showDryRunInspect = Url("$base$TOPICS_DRY_RUN_INSPECT")
    private val showTopicCreate = Url("$base$TOPICS_CREATE")
    private val showTopicWizard = Url("$base$TOPICS_WIZARD")
    private val showCreateTopicFromWizard = Url("$base$TOPICS_WIZARD_CREATE")

    fun showTopics() = showTopics.render()

    fun showAllTopicsTable() = showAllTopicsTable.render()

    fun showImportTopic(topicName: TopicName) = showImportTopic.render("topicName" to topicName)

    fun showEditTopic(topicName: TopicName) = showEditTopic.render("topicName" to topicName)

    fun showEditTopicOnBranch(
            topicName: TopicName,
            branch: String
    ) = showEditTopicOnBranch.render("topicName" to topicName, "branch" to branch)

    fun showSuggestEditTopic(topicName: TopicName) = showSuggestEditTopic.render("topicName" to topicName)

    fun showFixViolationsEdit(topicName: TopicName) = showFixViolationsEdit.render("topicName" to topicName)

    @JvmOverloads
    fun showCloneAddNewTopic(topicName: TopicName? = null) = showCloneAddNewTopic.render("topicName" to topicName)

    fun showDeleteTopic(topicName: TopicName) = showDeleteTopic.render("topicName" to topicName)

    fun showTopic(topicName: TopicName) = showTopic.render("topicName" to topicName)

    fun showTopicHistory(topicName: TopicName) = showTopicHistory.render("topicName" to topicName)

    fun showInspectTopicOnCluster(
            topicName: TopicName, clusterIdentifier: KafkaClusterIdentifier
    ) = showInspectTopicOnCluster.render("topicName" to topicName, "clusterIdentifier" to clusterIdentifier)

    fun showDryRunInspect() = showDryRunInspect.render()

    fun showTopicCreate() = showTopicCreate.render()

    fun showTopicWizard() = showTopicWizard.render()

    fun showCreateTopicFromWizard() = showCreateTopicFromWizard.render()

}