package eu.dotshell.pelo.generic.utils.search

/**
 * Utilities for search functionality
 */
object SearchUtils {
    // Pre-compiled regex patterns to avoid recompilation on every call
    private val COMBINING_MARKS_REGEX = "\\p{M}+".toRegex()
    private val NON_ALNUM_REGEX = "[^\\p{Alnum}]".toRegex()
    private val WHITESPACE_REGEX = "\\s+".toRegex()

    /**
     * Mapping of common accented characters to their ASCII equivalents.
     * Used as a fallback for Unicode normalization across all KMP platforms.
     */
    private val ACCENT_MAP = mapOf(
        'à' to 'a', 'á' to 'a', 'â' to 'a', 'ã' to 'a', 'ä' to 'a', 'å' to 'a',
        'æ' to 'a', 'ç' to 'c', 'è' to 'e', 'é' to 'e', 'ê' to 'e', 'ë' to 'e',
        'ì' to 'i', 'í' to 'i', 'î' to 'i', 'ï' to 'i', 'ð' to 'd', 'ñ' to 'n',
        'ò' to 'o', 'ó' to 'o', 'ô' to 'o', 'õ' to 'o', 'ö' to 'o', 'ø' to 'o',
        'ù' to 'u', 'ú' to 'u', 'û' to 'u', 'ü' to 'u', 'ý' to 'y', 'ÿ' to 'y',
        'À' to 'a', 'Á' to 'a', 'Â' to 'a', 'Ã' to 'a', 'Ä' to 'a', 'Å' to 'a',
        'Æ' to 'a', 'Ç' to 'c', 'È' to 'e', 'É' to 'e', 'Ê' to 'e', 'Ë' to 'e',
        'Ì' to 'i', 'Í' to 'i', 'Î' to 'i', 'Ï' to 'i', 'Ð' to 'd', 'Ñ' to 'n',
        'Ò' to 'o', 'Ó' to 'o', 'Ô' to 'o', 'Õ' to 'o', 'Ö' to 'o', 'Ø' to 'o',
        'Ù' to 'u', 'Ú' to 'u', 'Û' to 'u', 'Ü' to 'u', 'Ý' to 'y', 'Ÿ' to 'y',
        'Œ' to 'o', 'œ' to 'o', 'Š' to 's', 'š' to 's', 'Ž' to 'z', 'ž' to 'z'
    )

    /**
     * Remove diacritics/accents from a string using a character mapping approach.
     * Works on all KMP platforms without java.text.Normalizer.
     */
    private fun removeDiacritics(text: String): String {
        return buildString(text.length) {
            for (char in text) {
                append(ACCENT_MAP[char] ?: char)
            }
        }
    }

    /**
     * Normalize a string for fuzzy search matching:
     * - Removes accents/diacritics
     * - Converts to lowercase
     * - Replaces multiple spaces with single space
     * - Trims leading/trailing spaces
     *
     * This allows flexible matching:
     * - "Saint Denis" matches "Saint-Denis"
     * - "PERRIERE" matches "Perrière"
     * - "élysée" matches "Elysee"
     */
    fun normalizeForSearch(text: String): String {
        // Remove diacritics/accents
        val normalized = removeDiacritics(text)

        // Lowercase and normalize whitespace
        return normalized.lowercase()
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    /**
     * Normalize a stop name key for comparison (accent-insensitive, case-insensitive,
     * non-alphanumeric replaced by spaces, collapsed whitespace).
     */
    fun normalizeStopKey(raw: String): String {
        return removeDiacritics(raw)
            .lowercase()
            .replace(NON_ALNUM_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    /**
     * Check if a text contains a query with fuzzy matching
     * (case-insensitive, accent-insensitive, space-to-dash flexible)
     * Supports multi-word queries: all words must appear in the text
     * Optimized to minimize allocations and string operations
     */
    fun fuzzyContains(text: String, query: String): Boolean {
        return fuzzyContainsNormalized(normalizeForSearch(text), normalizeForSearch(query))
    }

    /**
     * Optimized version that accepts pre-normalized text and query.
     * Use this when searching through many items with cached normalized names.
     */
    fun fuzzyContainsNormalized(normalizedText: String, normalizedQuery: String): Boolean {
        if (normalizedQuery.isEmpty()) return true
        if (normalizedText.isEmpty()) return false

        // Check if query has multiple words
        val queryWords = normalizedQuery.split(" ").filter { it.isNotEmpty() }

        if (queryWords.size > 1) {
            // Multi-word: all words must appear in text (in any order)
            // Also check with hyphens and spaces swapped
            val textWithSpaces = normalizedText.replace('-', ' ')
            val textWithHyphens = normalizedText.replace(' ', '-')

            return queryWords.all { word ->
                normalizedText.contains(word) ||
                        textWithSpaces.contains(word) ||
                        textWithHyphens.contains(word)
            }
        }

        // Single word: try direct match first (most common case)
        if (normalizedText.contains(normalizedQuery)) {
            return true
        }

        // Only do expensive operations if direct match failed
        // Check if we need to handle hyphens/spaces at all
        if (!normalizedText.contains('-') && !normalizedQuery.contains(' ') &&
            !normalizedText.contains(' ') && !normalizedQuery.contains('-')
        ) {
            return false // No hyphens or spaces to worry about
        }

        // Try with hyphens replaced by spaces in text
        if (normalizedText.contains('-')) {
            val textWithSpaces = normalizedText.replace('-', ' ')
            if (textWithSpaces.contains(normalizedQuery)) {
                return true
            }
        }

        // Try with spaces replaced by hyphens in query
        if (normalizedQuery.contains(' ')) {
            val queryWithHyphens = normalizedQuery.replace(' ', '-')
            if (normalizedText.contains(queryWithHyphens)) {
                return true
            }
        }

        return false
    }

    /**
     * Optimized version that accepts pre-normalized text and query.
     * Use this when searching through many items with cached normalized names.
     */
    fun fuzzyStartsWithNormalized(normalizedText: String, normalizedQuery: String): Boolean {
        if (normalizedQuery.isEmpty()) return true
        if (normalizedText.isEmpty()) return false

        // Try direct match (most common case)
        if (normalizedText.startsWith(normalizedQuery)) {
            return true
        }

        // Only do expensive operations if needed
        if (!normalizedText.contains('-') && !normalizedQuery.contains(' ') &&
            !normalizedText.contains(' ') && !normalizedQuery.contains('-')
        ) {
            return false
        }

        // Try with hyphens replaced by spaces
        if (normalizedText.contains('-')) {
            val textWithSpaces = normalizedText.replace('-', ' ')
            if (textWithSpaces.startsWith(normalizedQuery)) {
                return true
            }
        }

        // Try with spaces replaced by hyphens in query
        if (normalizedQuery.contains(' ')) {
            val queryWithHyphens = normalizedQuery.replace(' ', '-')
            if (normalizedText.startsWith(queryWithHyphens)) {
                return true
            }
        }

        return false
    }
}
