package com.infobip.kafkistry.mcp

import com.infobip.kafkistry.appinfo.ModulesBuildInfoLoader
import com.infobip.kafkistry.service.background.BackgroundJobIssuesRegistry
import com.infobip.kafkistry.service.history.HistoryService
import com.infobip.kafkistry.service.scrapingstatus.ScrapingStatusService
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
open class KafkistrySystemTools(
    private val buildInfoLoader: ModulesBuildInfoLoader,
    private val backgroundIssuesRegistry: BackgroundJobIssuesRegistry,
    private val scrapingStatusService: ScrapingStatusService,
    private val historyService: HistoryService,
) {

    @McpTool(
        name = "kafkistry_get_build_info",
        description = """Returns build and version information for all Kafkistry modules.
Contains a list of module build info entries, each including the module name,
artifact version, build timestamp, and Git commit hash. Use to verify which
version of Kafkistry is running and whether all modules are consistently versioned.
Useful for diagnostics, support requests, and verifying deployment versions."""
    )
    open fun kafkistry_get_build_info(): String {
        return try {
            val result = buildInfoLoader.modulesInfos()
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_get_build_info", ex)
        }
    }

    @McpTool(
        name = "kafkistry_get_background_issues",
        description = """Returns any currently active issues reported by Kafkistry's background processing jobs.
Kafkistry runs background jobs for: cluster state scraping, autopilot remediation, record analysis,
and repository synchronization. When a background job encounters an error or enters a degraded state,
it registers an issue here. Each issue includes the job name, error message or description, and timestamp.
An empty list indicates all background jobs are operating normally."""
    )
    open fun kafkistry_get_background_issues(): String {
        return try {
            val result = backgroundIssuesRegistry.currentIssues()
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_get_background_issues", ex)
        }
    }

    @McpTool(
        name = "kafkistry_get_scraping_status",
        description = """Returns the current scraping status for each registered Kafka cluster.
Kafkistry periodically polls each cluster to refresh its in-memory state (topics, ACLs, quotas,
consumer groups, partition assignments, disk usage, etc.). Each entry includes: cluster identifier,
last successful scrape time, whether the last scrape attempt succeeded or failed, and any error details.
Use to understand data freshness — if a cluster was last scraped a long time ago, inspection results may be stale.
If clusterIdentifier is provided, only entries for that specific cluster are returned."""
    )
    open fun kafkistry_get_scraping_status(
        @McpToolParam(required = false, description = "Kafka cluster identifier to filter results; if omitted, all clusters are returned") clusterIdentifier: String?,
    ): String {
        return try {
            val result = scrapingStatusService.currentScrapingStatuses()
                .let { statuses ->
                    if (clusterIdentifier != null) statuses.filter { it.clusterIdentifier == clusterIdentifier }
                    else statuses
                }
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_get_scraping_status", ex)
        }
    }

    @McpTool(
        name = "kafkistry_get_history",
        description = """Returns the most recent change history entries from the Kafkistry registry.
History represents changes committed to the git-backed registry — topic creations, deletions,
configuration updates, ACL changes, quota changes, cluster registrations, and similar registry-level mutations.
Each entry typically includes: type of change, name of the affected entity, author, commit message/description, timestamp.
The count parameter controls how many recent entries are returned (default: 10)."""
    )
    open fun kafkistry_get_history(
        @McpToolParam(required = false, description = "Number of most recent history entries to return (default: 10)") count: Int?,
    ): String {
        return try {
            val result = historyService.getRecentHistory(count ?: 10)
            toMcpJson(result)
        } catch (ex: Exception) {
            mcpErrorJson("kafkistry_get_history", ex)
        }
    }
}
