package com.infobip.kafkistry.webapp.url

import com.infobip.kafkistry.webapp.WebHttpProperties
import com.infobip.kafkistry.webapp.menu.MenuItem
import com.infobip.kafkistry.webapp.url.AboutUrls.Companion.ABOUT
import com.infobip.kafkistry.webapp.url.AclsUrls.Companion.ACLS
import com.infobip.kafkistry.webapp.url.AutopilotUrls.Companion.AUTOPILOT
import com.infobip.kafkistry.webapp.url.ClustersManagementUrls.Companion.CLUSTERS_MANAGEMENT
import com.infobip.kafkistry.webapp.url.ClustersUrls.Companion.CLUSTERS
import com.infobip.kafkistry.webapp.url.CompareUrls.Companion.COMPARE
import com.infobip.kafkistry.webapp.url.ConsumeRecordsUrls.Companion.CONSUME
import com.infobip.kafkistry.webapp.url.ConsumerGroupsUrls.Companion.CONSUMER_GROUPS
import com.infobip.kafkistry.webapp.url.ProduceRecordsUrls.Companion.PRODUCE
import com.infobip.kafkistry.webapp.url.GitUrls.Companion.GIT
import com.infobip.kafkistry.webapp.url.HistoryUrls.Companion.HISTORY
import com.infobip.kafkistry.webapp.url.KStreamUrls.Companion.KSTREAM_APPS
import com.infobip.kafkistry.webapp.url.QuotasUrls.Companion.QUOTAS
import com.infobip.kafkistry.webapp.url.RecordsStructureUrls.Companion.RECORDS_STRUCTURE
import com.infobip.kafkistry.webapp.url.SqlUrls.Companion.SQL
import com.infobip.kafkistry.webapp.url.SearchUrls.Companion.SEARCH
import com.infobip.kafkistry.webapp.url.TopicsManagementUrls.Companion.TOPICS_MANAGEMENT
import com.infobip.kafkistry.webapp.url.TopicsUrls.Companion.TOPICS
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class AppUrl(
    httpProperties: WebHttpProperties,
) {
    private val base: String = httpProperties.rootPath

    private val main = MainUrls(base)
    private val about = AboutUrls("$base$ABOUT")
    private val acls = AclsUrls("$base$ACLS")
    private val clusters = ClustersUrls("$base$CLUSTERS")
    private val clustersManagement = ClustersManagementUrls("$base$CLUSTERS_MANAGEMENT")
    private val compare = CompareUrls("$base$COMPARE")
    private val consumeRecords = ConsumeRecordsUrls("$base$CONSUME")
    private val produceRecords = ProduceRecordsUrls("$base$PRODUCE")
    private val consumerGroups = ConsumerGroupsUrls("$base$CONSUMER_GROUPS")
    private val git = GitUrls("$base$GIT")
    private val history = HistoryUrls("$base$HISTORY")
    private val sql = SqlUrls("$base$SQL")
    private val topics = TopicsUrls("$base$TOPICS")
    private val topicsManagement = TopicsManagementUrls("$base$TOPICS_MANAGEMENT")
    private val recordsStructure = RecordsStructureUrls("$base$RECORDS_STRUCTURE")
    private val quotas = QuotasUrls("$base$QUOTAS")
    private val kStream = KStreamUrls("$base$KSTREAM_APPS")
    private val autopilot = AutopilotUrls("$base$AUTOPILOT")
    private val search = SearchUrls("$base$SEARCH")
    private val extraUrls: MutableMap<String, BaseUrls> = ConcurrentHashMap()

    fun basePath() = base

    fun main() = main
    fun about() = about
    fun acls() = acls
    fun clusters() = clusters
    fun clustersManagement() = clustersManagement
    fun compare() = compare
    fun consumeRecords() = consumeRecords
    fun produceRecords() = produceRecords
    fun consumerGroups() = consumerGroups
    fun git() = git
    fun history() = history
    fun sql() = sql
    fun topics() = topics
    fun topicsManagement() = topicsManagement
    fun recordsStructure() = recordsStructure
    fun quotas() = quotas
    fun kStream() = kStream
    fun autopilot() = autopilot
    fun search() = search

    fun menuItem(menuItem: MenuItem): String = basePath() + menuItem.urlPath

    fun registerExtra(name: String, baseUrls: BaseUrls) {
        extraUrls[name] = baseUrls
    }

    fun <U : BaseUrls> extraUrls(name: String, type: Class<U>): U {
        val extra = extraUrls[name] ?: throw IllegalArgumentException(
            "No registered extra urls named '$name', did you forget to register extra urls via ${AppUrl::class.simpleName}.registerExtra(..) ?"
        )
        if (type.isAssignableFrom(extra.javaClass)) {
            @Suppress("UNCHECKED_CAST")
            return extra as U
        } else {
            throw IllegalArgumentException("Registered extra urls '$name' has type ${extra.javaClass.name} but requested type is ${type.name}")
        }
    }

    fun visitExtraUrls(visitor: (name: String, baseUrls: BaseUrls) -> Unit) {
        extraUrls.forEach(visitor)
    }

    fun schema(): Map<String, Url> {
        return this.javaClass.declaredFields
            .filter { BaseUrls::class.java.isAssignableFrom(it.type) }
            .flatMap { field ->
                field.isAccessible = true
                val baseUrls = field.get(this) as BaseUrls
                baseUrls.schema().map { (key, url) -> "${field.name}.$key" to url }
            }
            .plus(
                extraUrls.map { it }.flatMap { (name, baseUrls) ->
                    baseUrls.schema().map { (key, url) -> "$name.$key" to url }
                }
            )
            .associate { it }
    }
}
