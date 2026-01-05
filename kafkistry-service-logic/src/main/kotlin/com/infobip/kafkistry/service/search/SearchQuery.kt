package com.infobip.kafkistry.service.search

/**
 * Parsed and tokenized search query.
 * Supports multiple search terms, field-specific searches, and phrase matching.
 */
data class SearchQuery(
    val rawQuery: String,
    val terms: List<SearchTerm>
) {
    /**
     * Check if the query is blank (no terms)
     */
    fun isBlank(): Boolean = terms.isEmpty()

    /**
     * Get all general (non-field-specific) search terms as lowercase strings
     */
    fun getGeneralTerms(): List<String> = terms
        .filterIsInstance<SearchTerm.General>()
        .map { it.value.lowercase() }

    /**
     * Get field-specific search terms for a given field
     */
    fun getFieldTerms(field: String): List<String> = terms
        .filterIsInstance<SearchTerm.FieldSpecific>()
        .filter { it.field.equals(field, ignoreCase = true) }
        .map { it.value.lowercase() }

    /**
     * Calculate match score based on how well the text matches all general query terms
     */
    fun calculateMatchScore(text: String?): Double {
        return calculateMatchScoreForTerms(text, getGeneralTerms())
    }

    /**
     * Calculate match score for specific terms in the text.
     * Used to avoid scoring the same term multiple times across different fields.
     *
     * @param text The text to score
     * @param termsToScore Only these terms will be considered for scoring
     * @return Score based on how well these specific terms match
     */
    fun calculateMatchScoreForTerms(text: String?, termsToScore: List<String>): Double {
        if (text == null || termsToScore.isEmpty()) return 0.0
        val textLower = text.lowercase()

        // Check for exact match
        if (termsToScore.size == 1 && textLower == termsToScore[0]) return 1.0

        // Check if starts with any of these terms
        val startsWithScore = termsToScore.maxOfOrNull { term ->
            if (textLower.startsWith(term)) 0.9 else 0.0
        } ?: 0.0
        if (startsWithScore > 0.0) return startsWithScore

        // Check how many of these terms match
        val matchingTerms = termsToScore.count { textLower.contains(it) }
        if (matchingTerms == 0) return 0.0

        // Score based on percentage of these terms that match
        val matchRatio = matchingTerms.toDouble() / termsToScore.size
        return 0.5 * matchRatio
    }
}

/**
 * Represents a single search term
 */
sealed class SearchTerm {
    /**
     * General search term (searches across all fields)
     */
    data class General(val value: String) : SearchTerm()

    /**
     * Field-specific search term (e.g., "owner:john")
     */
    data class FieldSpecific(val field: String, val value: String) : SearchTerm()
}

/**
 * Parses raw query string into SearchQuery with tokenized terms.
 *
 * Supported syntax:
 * - Multiple terms: "user prod cluster" (searches for items containing all terms)
 * - Field-specific: "owner:john topic:events" (searches specific fields)
 * - Quoted phrases: "\"user service\"" (treats as single term)
 * - Mixed: "owner:john prod cluster" (combines field-specific and general terms)
 */
object QueryParser {

    /**
     * Parse a raw query string into a SearchQuery
     */
    fun parse(rawQuery: String): SearchQuery {
        if (rawQuery.isBlank()) {
            return SearchQuery(rawQuery, emptyList())
        }

        val terms = mutableListOf<SearchTerm>()
        val tokens = tokenize(rawQuery)

        for (token in tokens) {
            // Check if it's a field-specific term (contains ':')
            val colonIndex = token.indexOf(':')
            if (colonIndex > 0 && colonIndex < token.length - 1) {
                val field = token.substring(0, colonIndex)
                val value = token.substring(colonIndex + 1)
                if (field.isNotBlank() && value.isNotBlank()) {
                    terms.add(SearchTerm.FieldSpecific(field, value))
                    continue
                }
            }

            // Otherwise, it's a general search term
            if (token.isNotBlank()) {
                terms.add(SearchTerm.General(token))
            }
        }

        return SearchQuery(rawQuery, terms)
    }

    /**
     * Tokenize the query string, respecting quoted phrases
     */
    private fun tokenize(query: String): List<String> {
        val tokens = mutableListOf<String>()
        var currentToken = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < query.length) {
            val char = query[i]

            when {
                char == '"' -> {
                    inQuotes = !inQuotes
                    // Don't include the quote character itself
                }
                char.isWhitespace() && !inQuotes -> {
                    // End of token
                    if (currentToken.isNotEmpty()) {
                        tokens.add(currentToken.toString())
                        currentToken = StringBuilder()
                    }
                }
                else -> {
                    currentToken.append(char)
                }
            }

            i++
        }

        // Add the last token
        if (currentToken.isNotEmpty()) {
            tokens.add(currentToken.toString())
        }

        return tokens
    }
}
