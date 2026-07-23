package app.lazydex.scraper.source

import app.lazydex.domain.model.MediaCategory
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class RoyalRoadSource(private val okHttpClient: OkHttpClient) : Source {
    override val id: String = "royalroad"
    override val name: String = "Royal Road"
    override val baseUrl: String = "https://www.royalroad.com"
    override val urlPattern: Regex = Regex("""https?://(?:www\.)?royalroad\.com/fiction/.*""")
    override val isSearchable: Boolean = true
    override val categoryHint: MediaCategory = MediaCategory.NOVEL

    override suspend fun scrape(url: String): ScrapedMetadata? {
        return try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val doc = Jsoup.parse(body, url)

                val title = doc.select("h1.font-red-sunglo, h1").first()?.text()?.trim() ?: doc.title().trim()

                val imageUrl = doc.select("meta[property=og:image]").attr("content").ifBlank {
                    doc.select(".cover-art-container img").first()?.absUrl("src") ?: ""
                }

                val author = doc.select("h4 a[href*=/profile/], .author-name a").first()?.text()?.trim() ?: ""
                val description = doc.select("div.description, .fiction-info .description").firstOrNull()?.html()?.trim() ?: ""

                val tags = doc.select(".tags a, .tags-label, a.label-sm").map { it.text().trim() }.filter { it.isNotBlank() }

                ScrapedMetadata(
                    title = title,
                    imageUrl = imageUrl,
                    author = author,
                    description = description,
                    tags = tags,
                    category = MediaCategory.NOVEL
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun search(query: String): List<SearchResult> {
        return try {
            val searchUrl = "$baseUrl/fiction/search?title=${java.net.URLEncoder.encode(query, "UTF-8")}"
            val request = Request.Builder().url(searchUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val doc = Jsoup.parse(body, searchUrl)

                doc.select(".fiction-list-item, .search-result").mapNotNull { card ->
                    val link = card.select("h2.title a, .fiction-title a").first() ?: return@mapNotNull null
                    val title = link.text().trim()
                    val href = link.absUrl("href")
                    val img = card.select("img").first()?.absUrl("src")
                    val author = card.select(".author, a[href*=/profile/]").text().trim()
                    val desc = card.select(".description").text().trim()
                    if (title.isBlank()) null else SearchResult(title = title, url = href, imageUrl = img, author = author, description = desc)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
