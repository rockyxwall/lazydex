package app.lazydex.scraper.source

import app.lazydex.domain.model.MediaCategory
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class WtrLabSource(private val okHttpClient: OkHttpClient) : Source {
    override val id: String = "wtrlab"
    override val name: String = "WTR-LAB"
    override val baseUrl: String = "https://wtr-lab.com"
    override val urlPattern: Regex = Regex("""https?://(?:www\.)?wtr-lab\.com/.*""")
    override val isSearchable: Boolean = true
    override val categoryHint: MediaCategory = MediaCategory.NOVEL

    override suspend fun scrape(url: String): ScrapedMetadata? {
        return try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val doc = Jsoup.parse(body, url)

                val title = doc.select("h1, meta[property=og:title]").firstOrNull()?.let {
                    if (it.tagName() == "meta") it.attr("content") else it.text()
                }?.trim() ?: doc.title().trim()

                val imageUrl = doc.select("meta[property=og:image]").attr("content").ifBlank {
                    doc.select(".series-cover img, .novel-cover img, img").first()?.absUrl("src") ?: ""
                }

                val author = doc.select(".author, a[href*=/author/]").first()?.text()?.trim() ?: ""
                val description = doc.select(".description, .synopsis, meta[property=og:description]").firstOrNull()?.let {
                    if (it.tagName() == "meta") it.attr("content") else it.html()
                }?.trim() ?: ""

                val genres = doc.select("a[href*=/genre/], .genre-badge, .genre").map { it.text().trim() }.filter { it.isNotBlank() }

                ScrapedMetadata(
                    title = title,
                    imageUrl = imageUrl,
                    author = author,
                    description = description,
                    genres = genres,
                    category = MediaCategory.NOVEL
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun search(query: String): List<SearchResult> {
        return try {
            val searchUrl = "$baseUrl/en/novel-finder?search=${java.net.URLEncoder.encode(query, "UTF-8")}"
            val request = Request.Builder().url(searchUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val doc = Jsoup.parse(body, searchUrl)

                doc.select(".novel-item, .series-card, .novel-card").mapNotNull { card ->
                    val link = card.select("a[href*=/novel/]").first() ?: return@mapNotNull null
                    val title = card.select(".title, h2, h3").text().ifBlank { link.text() }.trim()
                    val href = link.absUrl("href")
                    val img = card.select("img").first()?.absUrl("src")
                    SearchResult(title = title, url = href, imageUrl = img)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
