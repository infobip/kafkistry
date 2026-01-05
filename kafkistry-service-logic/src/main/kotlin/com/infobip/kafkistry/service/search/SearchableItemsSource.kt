package com.infobip.kafkistry.service.search

/**
 * Interface for providing searchable items to the global search feature.
 * Implementations are auto-discovered via Spring component scanning.
 *
 * Sources are only responsible for supplying items - matching, scoring,
 * and sorting is handled by SearchService.
 *
 * Each item returned by listAll() includes its own category information,
 * so there's no need for the source to declare a category separately.
 *
 * Similar to SqlDataSource<T> pattern, this allows extensibility by simply
 * implementing the interface and annotating with @Component.
 */
interface SearchableItemsSource {

    /**
     * Get all available items from this source.
     * The SearchService will handle filtering, scoring, and sorting.
     *
     * Each SearchableItem should include:
     * - title: Primary searchable text
     * - category: Category for grouping (instantiated by the source)
     * - url: Navigation target
     * - metadata: Additional searchable fields
     *
     * @return All available items with their metadata for searching
     */
    fun listAll(): List<SearchableItem>

    /**
     * Get the categories provided by this source.
     * This allows SearchService and SearchApi to obtain category information
     * without having to list all items and extract categories from them.
     *
     * @return Set of categories provided by this source
     */
    fun getCategories(): Set<SearchCategory>
}

/**
 * Represents a single searchable item.
 * Sources provide items with metadata; SearchService calculates scores during search.
 */
data class SearchableItem(
    val title: String,                          // Primary display text (e.g., topic name)
    val subtitle: String? = null,               // Secondary text (e.g., cluster name)
    val description: String? = null,            // Additional info (e.g., owner, status)
    val url: String,                            // Navigation URL
    val category: SearchCategory,               // Category for grouping
    val metadata: Map<String, String> = emptyMap(), // Additional searchable fields (owner, producer, etc.)
    val scoreFactor: Double = 1.0,
)

/**
 * Represents a search result item with scoring and match information.
 * Contains the original searchable item plus information about which fields matched
 * and what terms were found, enabling UI highlighting.
 */
data class SearchResultItem(
    val item: SearchableItem,                   // The original searchable item
    val score: Double,                          // Relevance score (higher = better match)
    val matches: MatchedFields                  // Information about which fields matched
) {
    // Convenience accessors for common item properties
    val title: String get() = item.title
    val subtitle: String? get() = item.subtitle
    val description: String? get() = item.description
    val url: String get() = item.url
    val category: SearchCategory get() = item.category
    val metadata: Map<String, String> get() = item.metadata
}

/**
 * Information about which fields matched the search query.
 * Each field contains the matched terms that were found in that field.
 */
data class MatchedFields(
    val titleMatches: List<String> = emptyList(),           // Terms that matched in title
    val subtitleMatches: List<String> = emptyList(),        // Terms that matched in subtitle
    val descriptionMatches: List<String> = emptyList(),     // Terms that matched in description
    val metadataMatches: Map<String, List<String>> = emptyMap() // Terms that matched in metadata fields
) {
    /**
     * Check if any field has matches
     */
    fun hasMatches(): Boolean =
        titleMatches.isNotEmpty() ||
        subtitleMatches.isNotEmpty() ||
        descriptionMatches.isNotEmpty() ||
        metadataMatches.isNotEmpty()
}

/**
 * Search result category.
 * Data class allows for extensibility - each SearchableItemsSource should instantiate its own category.
 *
 * Each source defines its own category instance with:
 * - Unique ID (e.g., "TOPICS", "CLUSTERS")
 * - Display name for UI
 * - Icon CSS class
 * - Default priority for ordering (lower = higher priority)
 *
 * Example:
 * ```
 * private val myCategory = SearchCategory(
 *     id = "MY_CATEGORY",
 *     displayName = "My Items",
 *     icon = "icon-my-items",
 *     defaultPriority = 100
 * )
 * ```
 */
data class SearchCategory(
    val id: String,                // Unique identifier
    val displayName: String,       // Human-readable name
    val icon: String,              // CSS class or icon identifier
    val defaultPriority: Int       // Default priority for ordering
)

/**
 * Search results as a flat list sorted by relevance
 */
data class SearchResults(
    val query: String,
    val totalResults: Int,
    val results: List<SearchResultItem>,
    val executionTimeMs: Long
)
