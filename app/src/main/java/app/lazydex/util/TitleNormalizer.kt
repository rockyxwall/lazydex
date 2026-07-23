package app.lazydex.util

import java.util.Locale

object TitleNormalizer {
    /**
     * Normalizes a title for Tier 3 matching:
     * - Converts to lowercase
     * - Strips bracketed text like [TV], (Official), etc.
     * - Removes punctuation and non-alphanumeric characters
     * - Collapses whitespace
     */
    fun normalize(title: String?): String {
        if (title.isNullOrBlank()) return ""
        return title.lowercase(Locale.ROOT)
            .replace(Regex("\\[[^\\]]*\\]|\\([^\\)]*\\)"), "")
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
