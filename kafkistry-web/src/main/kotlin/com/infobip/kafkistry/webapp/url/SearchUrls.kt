package com.infobip.kafkistry.webapp.url

class SearchUrls(private val base: String) : BaseUrls() {

    companion object {
        const val SEARCH = "/search"
    }

    private val showSearch = Url(base, listOf("query"))

    fun showSearch(query: String = ""): String {
        return if (query.isBlank()) {
            base
        } else {
            showSearch.render("query" to query)
        }
    }
}
