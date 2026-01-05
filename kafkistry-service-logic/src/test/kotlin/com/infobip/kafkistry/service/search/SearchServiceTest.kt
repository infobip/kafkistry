package com.infobip.kafkistry.service.search

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

class SearchServiceTest {

    private val topicsCategory = SearchCategory(
        id = "TOPICS",
        displayName = "Topics",
        icon = "icon-topic",
        defaultPriority = 10
    )

    private val clustersCategory = SearchCategory(
        id = "CLUSTERS",
        displayName = "Clusters",
        icon = "icon-cluster",
        defaultPriority = 20
    )

    private val topicsSource = object : SearchableItemsSource {
        override fun getCategories() = setOf(topicsCategory)
        override fun listAll() = listOf(
            SearchableItem(
                title = "user-events-topic",
                subtitle = "Events Team",
                description = "User activity events",
                url = "/topics/user-events-topic",
                category = topicsCategory,
                metadata = mapOf(
                    "owner" to "events-team",
                    "producer" to "user-service"
                )
            ),
            SearchableItem(
                title = "payment-transactions",
                subtitle = "Finance Team",
                description = "Payment processing events",
                url = "/topics/payment-transactions",
                category = topicsCategory,
                metadata = mapOf(
                    "owner" to "finance-team",
                    "producer" to "payment-service"
                )
            ),
            SearchableItem(
                title = "user-profile-updates",
                subtitle = "User Service",
                description = "Profile change notifications",
                url = "/topics/user-profile-updates",
                category = topicsCategory,
                metadata = mapOf(
                    "owner" to "user-team",
                    "producer" to "user-service"
                )
            )
        )
    }

    private val clustersSource = object : SearchableItemsSource {
        override fun getCategories() = setOf(clustersCategory)
        override fun listAll() = listOf(
            SearchableItem(
                title = "production-cluster",
                subtitle = "prod, live",
                description = "Production environment",
                url = "/clusters/production-cluster",
                category = clustersCategory,
                metadata = mapOf(
                    "tags" to "prod live",
                    "connection" to "kafka-prod:9092"
                )
            ),
            SearchableItem(
                title = "staging-cluster",
                subtitle = "staging, test",
                description = "Staging environment",
                url = "/clusters/staging-cluster",
                category = clustersCategory,
                metadata = mapOf(
                    "tags" to "staging test",
                    "connection" to "kafka-staging:9092"
                )
            )
        )
    }

    private fun createSearchService(vararg sources: SearchableItemsSource): SearchService {
        return SearchService(Optional.of(sources.toList()))
    }

    @Test
    fun `quickSearch - should find items matching single term`() {
        val service = createSearchService(topicsSource, clustersSource)

        val results = service.quickSearch("user")

        assertThat(results.totalResults).isEqualTo(2)
        assertThat(results.results.map { it.title }).containsExactlyInAnyOrder(
            "user-events-topic",
            "user-profile-updates"
        )
        assertThat(results.query).isEqualTo("user")
    }

    @Test
    fun `quickSearch - should return empty for blank query`() {
        val service = createSearchService(topicsSource)

        val results = service.quickSearch("")

        assertThat(results.totalResults).isEqualTo(0)
        assertThat(results.results).isEmpty()
    }

    @Test
    fun `quickSearch - should require all terms to match`() {
        val service = createSearchService(topicsSource, clustersSource)

        val results = service.quickSearch("user events")

        assertThat(results.totalResults).isEqualTo(1)
        assertThat(results.results.first().title).isEqualTo("user-events-topic")
    }

    @Test
    fun `fullSearch - should return all items for blank query`() {
        val service = createSearchService(topicsSource, clustersSource)

        val results = service.fullSearch("")

        assertThat(results.totalResults).isEqualTo(5)
        assertThat(results.results).hasSize(5)
    }

    @Test
    fun `fullSearch - should filter by category`() {
        val service = createSearchService(topicsSource, clustersSource)

        val results = service.fullSearch("", categories = setOf(topicsCategory))

        assertThat(results.totalResults).isEqualTo(3)
        assertThat(results.results.map { it.category }).allMatch { it == topicsCategory }
    }

    @Test
    fun `fullSearch - should respect maxResults limit`() {
        val service = createSearchService(topicsSource, clustersSource)

        val results = service.fullSearch("", maxResults = 2)

        assertThat(results.totalResults).isEqualTo(2)
        assertThat(results.results).hasSize(2)
    }

    @Test
    fun `search - should find matches in title`() {
        val service = createSearchService(topicsSource)

        val results = service.quickSearch("payment")

        assertThat(results.totalResults).isEqualTo(1)
        val result = results.results.first()
        assertThat(result.title).isEqualTo("payment-transactions")
        assertThat(result.matches.titleMatches).containsExactly("payment")
    }

    @Test
    fun `search - should find matches in subtitle`() {
        val service = createSearchService(topicsSource)

        val results = service.quickSearch("finance")

        assertThat(results.totalResults).isEqualTo(1)
        val result = results.results.first()
        assertThat(result.title).isEqualTo("payment-transactions")
        assertThat(result.matches.subtitleMatches).containsExactly("finance")
    }

    @Test
    fun `search - should find matches in metadata`() {
        val service = createSearchService(topicsSource)

        val results = service.quickSearch("payment-service")

        assertThat(results.totalResults).isEqualTo(1)
        val result = results.results.first()
        assertThat(result.title).isEqualTo("payment-transactions")
        assertThat(result.matches.metadataMatches).containsKey("producer")
    }

    @Test
    fun `search - should track all matches for highlighting`() {
        val service = createSearchService(topicsSource)

        val results = service.quickSearch("user service")

        assertThat(results.totalResults).isGreaterThan(0)
        val userEventsResult = results.results.first { it.title == "user-events-topic" }

        // Both terms should be highlighted even if only scored once
        assertThat(userEventsResult.matches.titleMatches).contains("user")
        assertThat(userEventsResult.matches.metadataMatches).containsKey("producer")
        assertThat(userEventsResult.matches.metadataMatches["producer"]).isNotNull().contains("service")
    }

    @Test
    fun `search - should score terms only once from highest priority field`() {
        val service = createSearchService(topicsSource)

        // Search for "user" which appears in both title and metadata
        val results = service.quickSearch("user")

        val userEventsResult = results.results.first { it.title == "user-events-topic" }
        val userProfileResult = results.results.first { it.title == "user-profile-updates" }

        // user-events-topic: "user" in title (scored) + other fields (highlighted but not scored)
        // user-profile-updates: "user" in title (scored) + subtitle + metadata (highlighted but not scored)

        // Both should have score <= 1.0 (not accumulated from multiple fields)
        assertThat(userEventsResult.score).isLessThanOrEqualTo(1.0)
        assertThat(userProfileResult.score).isLessThanOrEqualTo(1.0)

        // user-events-topic should score higher because "user" is more prominent in the title
        // (exact match at start vs. middle of compound word)
        assertThat(userEventsResult.score).isGreaterThan(0.0)
    }

    @Test
    fun `search - should prioritize title matches over other fields`() {
        val service = createSearchService(topicsSource)

        val results = service.quickSearch("payment")

        val paymentResult = results.results.first { it.title == "payment-transactions" }

        // "payment" appears in title - should have high score
        assertThat(paymentResult.score).isGreaterThan(0.5)
    }

    @Test
    fun `search - should handle field-specific queries`() {
        val service = createSearchService(topicsSource)

        val results = service.quickSearch("owner:events-team")

        assertThat(results.totalResults).isEqualTo(1)
        assertThat(results.results.first().title).isEqualTo("user-events-topic")
    }

    @Test
    fun `search - should filter out items not matching field-specific terms`() {
        val service = createSearchService(topicsSource)

        val results = service.quickSearch("user owner:finance-team")

        // Should not match user-events-topic because owner is events-team, not finance-team
        assertThat(results.totalResults).isEqualTo(0)
    }

    @Test
    fun `search - should sort by score descending`() {
        val service = createSearchService(topicsSource)

        val results = service.quickSearch("user")

        // Results should be sorted by score (highest first)
        val scores = results.results.map { it.score }
        assertThat(scores).isSortedAccordingTo(Comparator.reverseOrder())
    }

    @Test
    fun `search - should handle case-insensitive matching`() {
        val service = createSearchService(topicsSource)

        val results1 = service.quickSearch("USER")
        val results2 = service.quickSearch("user")
        val results3 = service.quickSearch("User")

        assertThat(results1.totalResults).isEqualTo(results2.totalResults)
        assertThat(results2.totalResults).isEqualTo(results3.totalResults)
    }

    @Test
    fun `search - should not match items missing required terms`() {
        val service = createSearchService(topicsSource, clustersSource)

        val results = service.quickSearch("nonexistent")

        assertThat(results.totalResults).isEqualTo(0)
    }

    @Test
    fun `getAllCategories - should return all categories from all sources`() {
        val service = createSearchService(topicsSource, clustersSource)

        val categories = service.getAllCategories()

        assertThat(categories).containsExactlyInAnyOrder(topicsCategory, clustersCategory)
    }

    @Test
    fun `getCategoryById - should find category by id case-insensitively`() {
        val service = createSearchService(topicsSource, clustersSource)

        val category1 = service.getCategoryById("TOPICS")
        val category2 = service.getCategoryById("topics")
        val category3 = service.getCategoryById("Topics")

        assertThat(category1).isEqualTo(topicsCategory)
        assertThat(category2).isEqualTo(topicsCategory)
        assertThat(category3).isEqualTo(topicsCategory)
    }

    @Test
    fun `getCategoryById - should return null for unknown category`() {
        val service = createSearchService(topicsSource)

        val category = service.getCategoryById("UNKNOWN")

        assertThat(category).isNull()
    }

    @Test
    fun `listAllByCategory - should return only items from specified category`() {
        val service = createSearchService(topicsSource, clustersSource)

        val topicItems = service.listAllByCategory(topicsCategory)

        assertThat(topicItems).hasSize(3)
        assertThat(topicItems.map { it.category }).allMatch { it == topicsCategory }
    }

    @Test
    fun `search - should respect scoreFactor from SearchableItem`() {
        val boostedSource = object : SearchableItemsSource {
            override fun getCategories() = setOf(topicsCategory)
            override fun listAll() = listOf(
                SearchableItem(
                    title = "important-topic",
                    url = "/topics/important",
                    category = topicsCategory,
                    scoreFactor = 2.0  // Boosted importance
                ),
                SearchableItem(
                    title = "normal-topic",
                    url = "/topics/normal",
                    category = topicsCategory,
                    scoreFactor = 1.0  // Normal importance
                )
            )
        }
        val service = createSearchService(boostedSource)

        val results = service.quickSearch("topic")

        // Both match "topic", but important-topic has 2x score factor
        val importantResult = results.results.first { it.title == "important-topic" }
        val normalResult = results.results.first { it.title == "normal-topic" }

        assertThat(importantResult.score).isGreaterThan(normalResult.score)
    }

    @Test
    fun `search - empty sources should return no results`() {
        val service = createSearchService()

        val results = service.quickSearch("anything")

        assertThat(results.totalResults).isEqualTo(0)
        assertThat(results.results).isEmpty()
    }

    @Test
    fun `search - should handle special regex characters in query`() {
        val specialSource = object : SearchableItemsSource {
            override fun getCategories() = setOf(topicsCategory)
            override fun listAll() = listOf(
                SearchableItem(
                    title = "topic.with.dots",
                    url = "/topics/dots",
                    category = topicsCategory
                ),
                SearchableItem(
                    title = "topic-with-dashes",
                    url = "/topics/dashes",
                    category = topicsCategory
                )
            )
        }
        val service = createSearchService(specialSource)

        val results = service.quickSearch("topic.with")

        // Should treat dots as literal, not regex
        assertThat(results.totalResults).isEqualTo(1)
        assertThat(results.results.first().title).isEqualTo("topic.with.dots")
    }
}
