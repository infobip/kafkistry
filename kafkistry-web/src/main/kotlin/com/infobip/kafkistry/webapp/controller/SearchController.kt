package com.infobip.kafkistry.webapp.controller

import com.infobip.kafkistry.api.SearchApi
import com.infobip.kafkistry.service.search.SearchService
import com.infobip.kafkistry.webapp.url.SearchUrls.Companion.SEARCH
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

@Controller
@RequestMapping("\${app.http.root-path}$SEARCH")
class SearchController(
    private val searchApi: SearchApi,
    private val searchService: SearchService
) : BaseController() {

    @GetMapping
    fun showSearchPage(
        @RequestParam("query", required = false) query: String?,
        @RequestParam("categories", required = false) categoryIds: String?,
        @RequestParam("maxResults", required = false) maxResults: String?,
    ): ModelAndView {
        val allCategories = searchService.getAllCategories()

        val categories = categoryIds?.split(",")
            ?.mapNotNull { id -> searchService.getCategoryById(id.trim()) }
            ?.toSet()

        // Parse maxResults: null = default 50, blank = unlimited, otherwise use value
        val maxResultsValue = when {
            maxResults == null -> 50  // Not provided: default 50
            maxResults.isBlank() -> Int.MAX_VALUE  // Blank: unlimited
            else -> maxResults.toIntOrNull() ?: 50  // Parse or default to 50
        }

        val searchResults = searchApi.fullSearch(query ?: "", categoryIds, maxResultsValue)

        return ModelAndView("search/search", mutableMapOf(
            "query" to (query ?: ""),
            "searchResults" to searchResults,
            "allCategories" to allCategories,
            "selectedCategories" to (categories ?: allCategories),
            "maxResults" to (maxResults ?: "50")
        ))
    }
}
