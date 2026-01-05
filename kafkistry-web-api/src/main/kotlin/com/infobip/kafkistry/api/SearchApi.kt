package com.infobip.kafkistry.api

import com.infobip.kafkistry.service.search.SearchResults
import com.infobip.kafkistry.service.search.SearchService
import com.infobip.kafkistry.service.search.SearchableItem
import org.springframework.web.bind.annotation.*

/**
 * Global search API for finding topics, clusters, ACLs, quotas, consumer groups, and admin tools
 */
@RestController
@RequestMapping("\${app.http.root-path}/api/search")
class SearchApi(
    private val searchService: SearchService
) {

    /**
     * Quick search for navbar dropdown (limited results)
     *
     * @param query Search query string
     * @param maxResults Maximum total results to return (default: 20)
     * @return Search results as flat list sorted by relevance
     */
    @GetMapping("/quick")
    fun quickSearch(
        @RequestParam("query") query: String,
        @RequestParam("maxResults", defaultValue = "20") maxResults: Int,
    ): SearchResults {
        return searchService.quickSearch(query, maxResults)
    }

    /**
     * Full search for dedicated search page
     *
     * @param query Search query string
     * @param categoryIds Optional comma-separated list of category IDs to filter (searches all if not provided)
     * @param maxResults Maximum total results to return (default: 100)
     * @return Detailed search results as flat list sorted by relevance
     */
    @GetMapping("/full")
    fun fullSearch(
        @RequestParam("query") query: String,
        @RequestParam("categories", required = false) categoryIds: String?,
        @RequestParam("maxResults", defaultValue = "100") maxResults: Int,
    ): SearchResults {
        val categories = categoryIds?.split(",")
            ?.mapNotNull { id -> searchService.getCategoryById(id.trim()) }
            ?.toSet()
        return searchService.fullSearch(query, categories, maxResults)
    }

    /**
     * List all items by category (for browsing)
     *
     * @param categoryId The category ID to list items from
     * @return All items in the specified category
     */
    @GetMapping("/category/{categoryId}")
    fun listByCategory(
        @PathVariable("categoryId") categoryId: String
    ): List<SearchableItem> {
        val category = searchService.getCategoryById(categoryId)
            ?: throw IllegalArgumentException("Unknown category: $categoryId")
        return searchService.listAllByCategory(category)
    }
}
