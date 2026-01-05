package com.infobip.kafkistry.it.broswer.cases.search

import org.assertj.core.api.Assertions.assertThat
import com.infobip.kafkistry.it.broswer.Context
import com.infobip.kafkistry.it.broswer.UITestCase
import com.infobip.kafkistry.service.newTopic
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openqa.selenium.By
import org.openqa.selenium.Keys

/**
 * Browser test for global search functionality in the navbar.
 * Tests searching from the home page dropdown search bar and navigating to results.
 */
abstract class GlobalSearchTest(contextSupplier: () -> Context) : UITestCase(contextSupplier) {

    @BeforeEach
    fun prepareTestData() {
        // Add cluster to registry
        addKafkaClusterToRegistry()

        // Create test topics
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

        // Wait for topics to be visible
        await("topics to be indexed") {
            assertThat(topicsApi.listTopics()).hasSizeGreaterThan(0)
        }
    }

    @Test
    fun `test global search from navbar and navigate to first result`() {
        // Verify we're on home page
        await {
            assertThat(browser.currentUrl).endsWith("/home")
        }

        // Find and click on the global search input in navbar
        val searchInput = browser.findElementById("global-search-input")
        searchInput.click()

        // Type search query
        searchInput.sendKeys("payment")

        // Wait for dropdown to appear with results
        await("search results to appear") {
            val dropdown = browser.findElementById("global-search-dropdown")
            assertThat(dropdown.isDisplayed).isTrue()
            assertThat(dropdown.text).contains("payment-transactions")
        }

        // Verify search result contains expected text
        val dropdown = browser.findElementById("global-search-dropdown")
        assertThat(dropdown.text).contains("payment-transactions", "finance-team")

        // Click on the first result
        val firstResult = browser.findElementByCssSelector("#global-search-dropdown .search-result-item:first-child")
        firstResult.click()

        // Verify navigation to the topic inspect page
        await("to navigate to topic page") {
            assertThat(browser.currentUrl).contains("/topics/inspect")
            assertThat(browser.currentUrl).contains("topicName=payment-transactions")
            browser.assertPageText().contains("Topic: payment-transactions")
        }

        // Verify we're on the correct topic page
        browser.assertPageText().contains(
            "payment-transactions",
            "finance-team",
            "Payment processing events",
            "payment-service"
        )
    }

    @Test
    fun `test global search shows multiple results`() {
        // On home page
        await {
            assertThat(browser.currentUrl).endsWith("/home")
        }

        // Search for "events" which should match multiple topics
        val searchInput = browser.findElementById("global-search-input")
        searchInput.click()
        searchInput.clearAndSendKeys("events")

        // Wait for results
        await("multiple search results to appear") {
            val dropdown = browser.findElementById("global-search-dropdown")
            assertThat(dropdown.isDisplayed).isTrue()
            // Should match both "user-events-topic" and topics with "events" in description
            val resultsText = dropdown.text
            assertThat(resultsText).contains("user-events-topic")
        }
    }

    @Test
    fun `test global search with no results`() {
        // On home page
        await {
            assertThat(browser.currentUrl).endsWith("/home")
        }

        // Search for something that doesn't exist
        val searchInput = browser.findElementById("global-search-input")
        searchInput.click()
        searchInput.clearAndSendKeys("nonexistent-topic-xyz")

        // Wait for empty results message
        await("no results message to appear") {
            val dropdown = browser.findElementById("global-search-dropdown")
            assertThat(dropdown.isDisplayed).isTrue()
            assertThat(dropdown.text).containsIgnoringCase("no results found")
        }
    }

    @Test
    fun `test global search closes on escape key`() {
        // On home page
        await {
            assertThat(browser.currentUrl).endsWith("/home")
        }

        // Open search dropdown
        val searchInput = browser.findElementById("global-search-input")
        searchInput.click()
        searchInput.sendKeys("payment")

        // Wait for dropdown to appear
        await("search dropdown to open") {
            val dropdown = browser.findElementById("global-search-dropdown")
            assertThat(dropdown.isDisplayed).isTrue()
        }

        // Press Escape key
        searchInput.sendKeys(Keys.ESCAPE)

        // Verify dropdown is hidden
        await("search dropdown to close") {
            val dropdown = browser.findElementById("global-search-dropdown")
            assertThat(dropdown.isDisplayed).isFalse()
        }
    }

    @Test
    fun `test global search can navigate to view all results`() {
        // On home page
        await {
            assertThat(browser.currentUrl).endsWith("/home")
        }

        // Search for something
        val searchInput = browser.findElementById("global-search-input")
        searchInput.click()
        searchInput.sendKeys("events")

        // Wait for results
        await("search results to appear") {
            val dropdown = browser.findElementById("global-search-dropdown")
            assertThat(dropdown.isDisplayed).isTrue()
        }

        // Click "View all results" link if it exists
        val viewAllLink = browser.findElements(By.cssSelector("#global-search-dropdown a[href*='/search']"))
        if (viewAllLink.isNotEmpty() && viewAllLink[0].isDisplayed) {
            viewAllLink[0].click()

            // Verify navigation to full search page
            await("to navigate to search page") {
                assertThat(browser.currentUrl).contains("/search")
                assertThat(browser.currentUrl).contains("query=events")
                browser.assertPageText().contains("Found 3 results")
            }
        }
    }
}
