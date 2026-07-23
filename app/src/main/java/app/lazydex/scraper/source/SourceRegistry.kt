package app.lazydex.scraper.source

class SourceRegistry(
    val sources: List<Source>
) {
    fun findSourceForUrl(url: String): Source {
        return sources.firstOrNull { it.urlPattern.matches(url) } ?: sources.first { it is GenericSource }
    }

    fun getSearchableSources(): List<Source> {
        return sources.filter { it.isSearchable }
    }

    suspend fun scrape(url: String): ScrapedMetadata? {
        val source = findSourceForUrl(url)
        return source.scrape(url)
    }
}
