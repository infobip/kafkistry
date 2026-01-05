package com.infobip.kafkistry.service.search

import org.springframework.stereotype.Service
import java.util.*
import kotlin.time.measureTimedValue

/**
 * Service for orchestrating search across all registered SearchableItemsSource implementations.
 * Handles matching, scoring, and sorting of search results.
 * Uses Spring's automatic component discovery to find all implementations.
 */
@Service
class SearchService(
    searchableSourcesOpt: Optional<List<SearchableItemsSource>>,
) {

    private val searchableSources: List<SearchableItemsSource> =
        searchableSourcesOpt.orElse(emptyList())

    /**
     * Quick search for navbar dropdown (limited results)
     * @param query Search query string (supports multiple terms and field-specific searches like "owner:john")
     * @param maxResults Maximum total results to return (default: 20)
     * @return Search results as flat list sorted by relevance
     */
    fun quickSearch(query: String, maxResults: Int = 20): SearchResults {
        // Quick search returns empty results for blank query
        if (query.isBlank()) {
            return SearchResults(query, 0, emptyList(), 0)
        }
        return search(query, categories = null, maxResults = maxResults)
    }

    /**
     * Full search for dedicated search page
     * @param query Search query string (supports multiple terms and field-specific searches like "owner:john")
     * @param categories Optional set of categories to filter by (searches all if null)
     * @param maxResults Maximum total results to return (default: 100)
     * @return Detailed search results as flat list sorted by relevance
     */
    fun fullSearch(
        query: String,
        categories: Set<SearchCategory>? = null,
        maxResults: Int = 100
    ): SearchResults {
        // Full search returns all items for blank query (wrapped in SearchResultItem with no matches)
        if (query.isBlank()) {
            val (allResults, executionTime) = measureTimedValue {
                allItemsSequence()
                    .filterByCategories(categories)
                    .sortedBy { it.category.defaultPriority }
                    .take(maxResults)
                    .map { SearchResultItem(it, 0.0, MatchedFields()) }
                    .toList()
            }
            return SearchResults(
                query = query,
                totalResults = allResults.size,
                results = allResults,
                executionTimeMs = executionTime.inWholeMilliseconds
            )
        }
        return search(query, categories, maxResults)
    }

    /**
     * Common search implementation for both quick and full search.
     * @param query Search query string (supports multiple terms and field-specific searches like "owner:john")
     * @param categories Optional set of categories to filter by (searches all if null)
     * @param maxResults Maximum total results to return
     * @return Search results as flat list sorted by relevance
     */
    private fun search(
        query: String,
        categories: Set<SearchCategory>?,
        maxResults: Int
    ): SearchResults {

        val (scoredResults, executionTime) = measureTimedValue  {
            val parsedQuery = QueryParser.parse(query.trim())
            allItemsSequence()
                .filterByCategories(categories)
                .map { scoreItem(it, parsedQuery) }
                .filter { it.score > 0.0 }
                .sortedBy { it.category.defaultPriority }
                .sortedByDescending { it.score }
                .take(maxResults)
                .toList()
        }

        return SearchResults(query,
            totalResults = scoredResults.size,
            results = scoredResults,
            executionTimeMs = executionTime.inWholeMilliseconds,
        )
    }

    private fun allItemsSequence(): Sequence<SearchableItem> = searchableSources.asSequence()
        .flatMap { it.listAll() }

    private fun Sequence<SearchableItem>.filterByCategories(categories: Set<SearchCategory>?): Sequence<SearchableItem> {
        //filter by category if specified
        return if (categories != null) {
            filter { it.category in categories }
        } else {
            this
        }
    }

    /**
     * List all items by category (for browsing)
     * @param category The category to list items from
     * @return All items in the specified category
     */
    fun listAllByCategory(category: SearchCategory): List<SearchableItem> {
        return searchableSources
            .flatMap { it.listAll() }
            .filter { it.category == category }
    }

    /**
     * Get all available categories from all sources
     * @return Set of all categories
     */
    fun getAllCategories(): Set<SearchCategory> {
        return searchableSources
            .flatMap { it.getCategories() }
            .toSet()
    }

    /**
     * Get a category by its ID from available categories
     * @param id The category ID
     * @return The category or null if not found
     */
    fun getCategoryById(id: String): SearchCategory? {
        return searchableSources
            .flatMap { it.getCategories() }
            .firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    /**
     * Calculate relevance score for a searchable item based on the query.
     * Checks field-specific terms first, then verifies all general terms match somewhere.
     * Accumulates scores from all matching fields with weighted priorities.
     * Returns SearchResultItem with match information for UI highlighting.
     */
    private fun scoreItem(item: SearchableItem, query: SearchQuery): SearchResultItem {
        // Check field-specific terms - if they don't match, score is 0
        for ((field, value) in item.metadata) {
            val fieldTerms = query.getFieldTerms(field)
            if (fieldTerms.isNotEmpty() && !fieldTerms.all { term -> value.lowercase().contains(term) }) {
                return SearchResultItem(item, 0.0, MatchedFields())
            }
        }

        // Collect all searchable text to check if all general terms match somewhere
        val generalTerms = query.getGeneralTerms()
        if (generalTerms.isNotEmpty()) {
            val allSearchableText = buildString {
                append(item.title.lowercase())
                append(" ")
                item.subtitle?.let { append(it.lowercase()).append(" ") }
                item.description?.let { append(it.lowercase()).append(" ") }
                item.metadata.values.forEach { append(it.lowercase()).append(" ") }
            }

            // If any general term doesn't match anywhere, score is 0
            if (!generalTerms.all { term -> allSearchableText.contains(term) }) {
                return SearchResultItem(item, 0.0, MatchedFields())
            }
        }

        // All terms match - now accumulate scores and track matches from all fields
        // Each term only contributes to score once, from the highest-priority field where it matches
        // But for highlighting, we track ALL matches in each field
        var totalScore = 0.0
        val titleMatches = mutableListOf<String>()
        val subtitleMatches = mutableListOf<String>()
        val descriptionMatches = mutableListOf<String>()
        val metadataMatches = mutableMapOf<String, List<String>>()
        val scoredTerms = mutableSetOf<String>() // Track terms that have already contributed to score

        // Title matches (highest priority: weight 1.0)
        val titleMatchedTerms = findMatchingTerms(item.title, generalTerms)
        if (titleMatchedTerms.isNotEmpty()) {
            titleMatches.addAll(titleMatchedTerms) // For highlighting
            val titleScore = query.calculateMatchScoreForTerms(item.title, titleMatchedTerms)
            totalScore += titleScore * 1.0
            scoredTerms.addAll(titleMatchedTerms)
        }

        // Subtitle matches (medium-high priority: weight 0.7)
        if (item.subtitle != null) {
            val allSubtitleMatches = findMatchingTerms(item.subtitle, generalTerms)
            if (allSubtitleMatches.isNotEmpty()) {
                subtitleMatches.addAll(allSubtitleMatches) // For highlighting - show all matches
                val termsToScore = allSubtitleMatches.filter { it !in scoredTerms }
                if (termsToScore.isNotEmpty()) {
                    val subtitleScore = query.calculateMatchScoreForTerms(item.subtitle, termsToScore)
                    totalScore += subtitleScore * 0.7
                    scoredTerms.addAll(termsToScore)
                }
            }
        }

        // Description matches (medium priority: weight 0.6)
        if (item.description != null) {
            val allDescriptionMatches = findMatchingTerms(item.description, generalTerms)
            if (allDescriptionMatches.isNotEmpty()) {
                descriptionMatches.addAll(allDescriptionMatches) // For highlighting - show all matches
                val termsToScore = allDescriptionMatches.filter { it !in scoredTerms }
                if (termsToScore.isNotEmpty()) {
                    val descriptionScore = query.calculateMatchScoreForTerms(item.description, termsToScore)
                    totalScore += descriptionScore * 0.6
                    scoredTerms.addAll(termsToScore)
                }
            }
        }

        // Metadata matches (lower priority: weight 0.5)
        for ((field, value) in item.metadata) {
            val allMetadataMatches = findMatchingTerms(value, generalTerms)
            if (allMetadataMatches.isNotEmpty()) {
                metadataMatches[field] = allMetadataMatches // For highlighting - show all matches
                val termsToScore = allMetadataMatches.filter { it !in scoredTerms }
                if (termsToScore.isNotEmpty()) {
                    val metadataScore = query.calculateMatchScoreForTerms(value, termsToScore)
                    totalScore += metadataScore * 0.5
                    scoredTerms.addAll(termsToScore)
                }
            }
        }

        // If no general terms were scored but query has field-specific terms that matched,
        // give a baseline score for field-specific matches
        if (totalScore == 0.0 && generalTerms.isEmpty() && query.terms.isNotEmpty()) {
            totalScore = 0.7 // Baseline score for field-specific only matches
        }

        val matchedFields = MatchedFields(
            titleMatches = titleMatches,
            subtitleMatches = subtitleMatches,
            descriptionMatches = descriptionMatches,
            metadataMatches = metadataMatches,
        )

        val finalScore = totalScore.coerceAtMost(1.0) * item.scoreFactor
        return SearchResultItem(item, finalScore, matchedFields)
    }

    /**
     * Find which terms from the query match in the given text
     */
    private fun findMatchingTerms(text: String, terms: List<String>): List<String> {
        val textLower = text.lowercase()
        return terms.filter { term -> textLower.contains(term) }
    }
}
