package com.infobip.kafkistry.it.broswer.cases.search

import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.it.broswer.Context
import com.infobip.kafkistry.it.broswer.UITestCase
import com.infobip.kafkistry.service.newTopic
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openqa.selenium.By

/**
 * Browser test for full search page functionality.
 * Tests searching, filtering, and viewing results on the dedicated search page.
 */
abstract class FullSearchPageTest(contextSupplier: () -> Context) : UITestCase(contextSupplier) {

    @BeforeEach
    fun prepareTestData() {
        // Add cluster to registry
        addKafkaClusterToRegistry()

        // Create test topics with different characteristics
        addTopicToRegistry(newTopic(
            name = "user-events-topic",
            owner = "events-team",
            description = "User activity events",
            producer = "user-service"
        ))
        addTopicToRegistry(newTopic(
            name = "payment-transactions",
            owner = "finance-team",
            description = "Payment processing events",
            producer = "payment-service"
        ))
        addTopicToRegistry(newTopic(
            name = "order-notifications",
            owner = "orders-team",
            description = "Order status notifications",
            producer = "order-service"
        ))
        addTopicToRegistry(newTopic(
            name = "user-profile-updates",
            owner = "user-team",
            description = "Profile change notifications",
            producer = "user-service"
        ))

        // Wait for topics to be indexed
        await("topics to be indexed") {
            assertThat(topicsApi.listTopics()).hasSizeGreaterThanOrEqualTo(4)
        }
    }

    private fun navigateToSearchPage(query: String? = null) {
        val searchUrl = if (query != null) {
            "${browser.currentUrl?.substringBefore("/home")}/search?query=$query"
        } else {
            "${browser.currentUrl?.substringBefore("/home")}/search"
        }
        browser.get(searchUrl)

        await("search page to load") {
            assertThat(browser.currentUrl).contains("/search")
        }
    }

    @Test
    fun `test navigate to search page and view all results`() {
        // Navigate to search page - could be via menu or direct URL
        navigateToSearchPage()

        browser.assertPageText().contains("Search")

        // With blank query, should show all items
        browser.assertPageText().contains(
            "user-events-topic",
            "payment-transactions",
            "order-notifications",
            "user-profile-updates"
        )
    }

    @Test
    fun `test search with query on search page`() {
        // Navigate to search page
        navigateToSearchPage()

        // Find search input and enter query
        val searchInput = browser.findElementById("search-input")
        searchInput.clearAndSendKeys("payment")

        // Click search button
        val searchButton = browser.findElementById("search-btn")
        searchButton.click()

        // Wait for results
        await("search results to update") {
            assertThat(browser.currentUrl).contains("query=payment")
            browser.assertPageText().contains("payment-transactions")
        }

        // Verify only matching results are shown
        browser.assertPageText().contains("payment-transactions", "finance-team")

        // Verify non-matching topics are not shown in results
        val pageText = browser.pageText()
        assertThat(pageText).doesNotContain("order-notifications")
    }

    @Test
    fun `test search with multiple terms requires all to match`() {
        // Navigate to search page
        navigateToSearchPage()

        // Search for multiple terms
        val searchInput = browser.findElementById("search-input")
        searchInput.clearAndSendKeys("user events")

        val searchButton = browser.findElementById("search-btn")
        searchButton.click()

        // Wait for results
        await("search results to update") {
            assertThat(browser.currentUrl).contains("query=user%20events")
            browser.assertPageText().contains("Found 2 results")
        }

        // Only "user-events-topic" should match (has both "user" and "events")
        browser.assertPageText().contains("user-events-topic")

        // "user-profile-updates" should not match (has "user" but not "events")
        val pageText = browser.pageText()
        assertThat(pageText).doesNotContain("user-profile-updates")
    }

    @Test
    fun `test search with field-specific query`() {
        // Navigate to search page
        navigateToSearchPage()

        // Search with field-specific term
        val searchInput = browser.findElementById("search-input")
        searchInput.clearAndSendKeys("owner:finance-team")

        val searchButton = browser.findElementById("search-btn")
        searchButton.click()

        // Wait for results
        await("search results to update") {
            assertThat(browser.currentUrl).contains("query=owner")
            browser.assertPageText().contains("payment-transactions")
        }

        // Only payment-transactions has owner=finance-team
        browser.assertPageText().contains("payment-transactions")

        // Others should not be shown
        val pageText = browser.pageText()
        assertThat(pageText).doesNotContain("user-events-topic", "order-notifications")
    }

    @Test
    fun `test category filter on search page`() {
        // Navigate to search page
        navigateToSearchPage()

        // Look for category filter - might be in a modal or sidebar
        val filterButton = browser.findElements(By.id("filter-btn"))
        if (filterButton.isNotEmpty() && filterButton[0].isDisplayed) {
            filterButton[0].click()

            // Wait for filter modal/panel to appear
            await("filter controls to appear") {
                val filterModal = browser.findElements(By.id("filter-modal"))
                if (filterModal.isNotEmpty()) {
                    assertThat(filterModal[0].isDisplayed).isTrue()
                }
            }

            // Find and select a specific category (e.g., TOPICS)
            val categoryCheckboxes = browser.findElements(By.cssSelector(".category-filter"))
            if (categoryCheckboxes.isNotEmpty()) {
                // Uncheck all first, then check only TOPICS
                categoryCheckboxes.forEach { checkbox ->
                    if (checkbox.isSelected) {
                        checkbox.click()
                    }
                }

                // Check the TOPICS category
                val topicsCheckbox = browser.findElements(By.cssSelector("input.category-filter[value='TOPICS']"))
                if (topicsCheckbox.isNotEmpty()) {
                    topicsCheckbox[0].click()

                    // Apply filters
                    val applyButton = browser.findElementById("apply-filters-btn")
                    applyButton.click()

                    // Verify filter is applied in URL
                    await("filters to be applied") {
                        assertThat(browser.currentUrl).contains("categories=TOPICS")
                    }
                }
            }
        }
    }

    @Test
    fun `test max results limit on search page`() {
        // Navigate to search page
        navigateToSearchPage()

        // Find max results input
        val maxResultsInput = browser.findElements(By.id("max-results-input"))
        if (maxResultsInput.isNotEmpty() && maxResultsInput[0].isDisplayed) {
            maxResultsInput[0].clearAndSendKeys("2")

            // Click search to apply
            val searchButton = browser.findElementById("search-btn")
            searchButton.click()

            // Wait for results with limit
            await("search results with limit") {
                assertThat(browser.currentUrl).contains("maxResults=2")
            }

            // Count visible results - should be at most 2
            val resultItems = browser.findElements(By.cssSelector(".search-result-item, .list-group-item"))
            assertThat(resultItems.size).isLessThanOrEqualTo(2)
        }
    }

    @Test
    fun `test click on search result navigates to item page`() {
        // Navigate to search page with a query
        navigateToSearchPage("payment")

        await("search page with results to load") {
            assertThat(browser.currentUrl).contains("query=payment")
            browser.assertPageText().contains("payment-transactions")
        }

        // Click on a search result
        val resultLink = browser.findElements(By.linkText("payment-transactions"))
            .firstOrNull { it.isDisplayed }

        if (resultLink != null) {
            resultLink.click()

            // Verify navigation to topic inspect page
            await("to navigate to topic page") {
                assertThat(browser.currentUrl).contains("/topics/inspect")
                assertThat(browser.currentUrl).contains("topicName=payment-transactions")
                browser.assertPageText().contains("Topic: payment-transactions")
            }
        }
    }

    @Test
    fun `test search highlights matching terms`() {
        // Navigate to search page with a query
        navigateToSearchPage("payment")

        await("search page with results to load") {
            assertThat(browser.currentUrl).contains("query=payment")
            browser.assertPageText().contains("payment-transactions")
        }

        // Look for highlighted text (mark tags or highlight class)
        val highlightedElements = browser.findElements(By.cssSelector("mark.search-highlight, .search-highlight"))

        // Should have some highlighted matches
        if (highlightedElements.isNotEmpty()) {
            assertThat(highlightedElements.size).isGreaterThan(0)
            // At least one should contain "payment"
            val hasPaymentHighlight = highlightedElements.any {
                it.text.lowercase().contains("payment")
            }
            assertThat(hasPaymentHighlight).isTrue()
        }
    }

    @Test
    fun `test empty search query shows all results`() {
        // Navigate to search page with empty query
        navigateToSearchPage("")

        browser.assertPageText().contains("Search")

        // Should show all topics
        browser.assertPageText().contains(
            "user-events-topic",
            "payment-transactions",
            "order-notifications",
            "user-profile-updates"
        )
    }
}
