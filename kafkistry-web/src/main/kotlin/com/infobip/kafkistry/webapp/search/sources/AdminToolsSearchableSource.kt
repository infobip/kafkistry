package com.infobip.kafkistry.webapp.search.sources

import com.infobip.kafkistry.service.search.*
import com.infobip.kafkistry.webapp.url.AppUrl
import org.springframework.stereotype.Component

@Component
class AdminToolsSearchableSource(
    private val appUrl: AppUrl
) : SearchableItemsSource {

    private val category = SearchCategory(
        id = "ADMIN_TOOLS",
        displayName = "Admin & Tools",
        icon = "icon-admin",
        defaultPriority = 60
    )

    override fun getCategories(): Set<SearchCategory> = setOf(category)

    private val adminTools = listOf(
        AdminTool("SQL Query Tool", "Execute SQL queries on Kafka data", "sql", listOf("sql", "query", "search", "data")),
        AdminTool("Consume Records", "Consume records from topics", "consume", listOf("consume", "read", "messages", "records")),
        AdminTool("Produce Records", "Produce records to topics", "produce", listOf("produce", "write", "send", "publish")),
        AdminTool("Records Structure", "View and manage record structures", "records structure", listOf("schema", "avro", "structure", "format")),
        AdminTool("KStream Apps", "KStream applications overview", "kstream", listOf("kstream", "streams", "kafka streams")),
        AdminTool("Git History", "View repository change history", "history", listOf("git", "history", "changes", "commits")),
        AdminTool("Build Info", "Application build information", "build", listOf("build", "version", "info")),
        AdminTool("Environment", "Environment properties", "environment", listOf("environment", "properties", "config")),
        AdminTool("Background Jobs", "Background job statuses", "background", listOf("jobs", "background", "tasks")),
        AdminTool("Scraping Statuses", "Cluster scraping statuses", "scraping", listOf("scraping", "status", "refresh")),
        AdminTool("User Sessions", "Active user sessions", "sessions", listOf("users", "sessions", "active"))
    )

    override fun listAll(): List<SearchableItem> {
        return adminTools.map { tool ->
            SearchableItem(
                title = tool.name,
                subtitle = null,
                description = tool.description,
                url = getToolUrl(tool.urlKey),
                category = category,
                metadata = mapOf(
                    "keywords" to tool.keywords.joinToString(" ")
                )
            )
        }
    }

    private fun getToolUrl(key: String): String = when(key) {
        "sql" -> appUrl.sql().showSqlPage()
        "consume" -> appUrl.consumeRecords().showConsumePage()
        "produce" -> appUrl.produceRecords().showProducePage()
        "records structure" -> appUrl.recordsStructure().showMenuPage()
        "kstream" -> appUrl.kStream().showAll()
        "history" -> appUrl.history().showRecent()
        "build" -> appUrl.about().showBuildInfo()
        "environment" -> appUrl.about().showEnvironment()
        "background" -> appUrl.about().showBackgroundJobs()
        "scraping" -> appUrl.about().showScrapingStatuses()
        "sessions" -> appUrl.about().showUsersSessions()
        else -> appUrl.basePath()
    }

    private data class AdminTool(
        val name: String,
        val description: String,
        val urlKey: String,
        val keywords: List<String>
    )
}
