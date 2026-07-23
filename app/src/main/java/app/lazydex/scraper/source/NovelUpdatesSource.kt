package app.lazydex.scraper.source

import app.lazydex.domain.model.MediaCategory
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class NovelUpdatesSource(private val okHttpClient: OkHttpClient) : Source {
    override val id: String = "novelupdates"
    override val name: String = "NovelUpdates"
    override val baseUrl: String = "https://www.novelupdates.com"
    override val urlPattern: Regex = Regex("""https?://(?:www\.)?novelupdates\.com/series/.*""")
    override val isSearchable: Boolean = true
    override val categoryHint: MediaCategory = MediaCategory.NOVEL

    override suspend fun scrape(url: String): ScrapedMetadata? {
        return try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val doc = Jsoup.parse(body, url)

                val title = doc.select("h1.series-title, div.seriestitlenew").text().trim().ifBlank {
                    doc.select("meta[property=og:title]").attr("content").trim()
                }

                val imageUrl = doc.select(".seriesimg img, meta[property=og:image]").firstOrNull()?.let {
                    if (it.tagName() == "meta") it.attr("content") else it.absUrl("src")
                } ?: ""

                val author = doc.select("#showauthors a, .author").text().trim()
                val description = doc.select("#editdescription, div.description").firstOrNull()?.html()?.trim()
                    ?: doc.select("meta[property=og:description]").attr("content").trim()

                val genres = doc.select("#seriesgenre a").map { it.text().trim() }.filter { it.isNotBlank() }
                val tags = doc.select("#seriestag a").map { it.text().trim() }.filter { it.isNotBlank() }
                val altTitles = doc.select("#editassociated").text().split("\n", ",").map { it.trim() }.filter { it.isNotBlank() }

                ScrapedMetadata(
                    title = title,
                    imageUrl = imageUrl,
                    alternativeTitles = altTitles,
                    author = author,
                    description = description,
                    genres = genres,
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
            val searchUrl = "$baseUrl/?s=${java.net.URLEncoder.encode(query, "UTF-8")}&post_type=seriespools"
            val request = Request.Builder().url(searchUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val doc = Jsoup.parse(body, searchUrl)

                doc.select("div.search_main_box, tr.post").mapNotNull { card ->
                    val link = card.select("a[href*=/series/]").first() ?: return@mapNotNull null
                    val title = link.text().trim()
                    val href = link.absUrl("href")
                    val img = card.select("img").first()?.absUrl("src")
                    val desc = card.select(".search_body, .description").text().trim()
                    if (title.isBlank()) null else SearchResult(title = title, url = href, imageUrl = img, description = desc)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
