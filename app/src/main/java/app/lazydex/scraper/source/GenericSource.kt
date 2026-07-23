package app.lazydex.scraper.source

import app.lazydex.domain.model.MediaCategory
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException

class GenericSource(private val okHttpClient: OkHttpClient) : Source {
    override val id: String = "generic"
    override val name: String = "Web Source (Fallback)"
    override val baseUrl: String = ""
    override val urlPattern: Regex = Regex(""".*""")
    override val isSearchable: Boolean = false
    override val categoryHint: MediaCategory? = null

    override suspend fun scrape(url: String): ScrapedMetadata? {
        return try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val doc = Jsoup.parse(body, url)

                val title = doc.select("meta[property=og:title]").attr("content").ifBlank {
                    doc.select("meta[name=twitter:title]").attr("content").ifBlank { doc.title() }
                }.trim()

                val imageUrl = doc.select("meta[property=og:image]").attr("content").ifBlank {
                    doc.select("meta[name=twitter:image]").attr("content")
                }.trim()

                val description = doc.select("meta[property=og:description]").attr("content").ifBlank {
                    doc.select("meta[name=description]").attr("content")
                }.trim()

                val keywords = doc.select("meta[name=keywords]").attr("content")
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                if (title.isBlank()) null
                else ScrapedMetadata(
                    title = title,
                    imageUrl = imageUrl,
                    description = description,
                    genres = keywords
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun search(query: String): List<SearchResult> = emptyList()
}
