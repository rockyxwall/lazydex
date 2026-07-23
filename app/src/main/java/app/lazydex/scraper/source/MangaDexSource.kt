package app.lazydex.scraper.source

import app.lazydex.domain.model.MediaCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

class MangaDexSource(private val okHttpClient: OkHttpClient) : Source {
    override val id: String = "mangadex"
    override val name: String = "MangaDex"
    override val baseUrl: String = "https://mangadex.org"
    override val urlPattern: Regex = Regex("""https?://(?:www\.)?mangadex\.org/title/([a-f0-9\-]+).*""")
    override val isSearchable: Boolean = true
    override val categoryHint: MediaCategory = MediaCategory.MANGA

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun scrape(url: String): ScrapedMetadata? {
        val match = urlPattern.find(url) ?: return null
        val mangaId = match.groupValues[1]

        val apiUrl = "https://api.mangadex.org/manga/$mangaId?includes[]=author&includes[]=cover_art"
        return try {
            val request = Request.Builder().url(apiUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val root = json.parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonObject ?: return null
                val attributes = data["attributes"]?.jsonObject ?: return null

                val title = attributes["title"]?.jsonObject?.values?.firstOrNull()?.jsonPrimitive?.content
                    ?: attributes["altTitles"]?.jsonArray?.firstOrNull()?.jsonObject?.values?.firstOrNull()?.jsonPrimitive?.content
                    ?: "Untitled"

                val description = attributes["description"]?.jsonObject?.get("en")?.jsonPrimitive?.content ?: ""

                val altTitles = attributes["altTitles"]?.jsonArray?.mapNotNull { obj ->
                    obj.jsonObject.values.firstOrNull()?.jsonPrimitive?.content
                } ?: emptyList()

                val tags = attributes["tags"]?.jsonArray?.mapNotNull { tag ->
                    tag.jsonObject["attributes"]?.jsonObject?.get("name")?.jsonObject?.get("en")?.jsonPrimitive?.content
                } ?: emptyList()

                val relationships = data["relationships"]?.jsonArray ?: emptyList()
                val author = relationships.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "author" }
                    ?.jsonObject?.get("attributes")?.jsonObject?.get("name")?.jsonPrimitive?.content ?: ""

                val coverFileName = relationships.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "cover_art" }
                    ?.jsonObject?.get("attributes")?.jsonObject?.get("fileName")?.jsonPrimitive?.content

                val imageUrl = if (coverFileName != null) "https://uploads.mangadex.org/covers/$mangaId/$coverFileName" else ""

                ScrapedMetadata(
                    title = title,
                    imageUrl = imageUrl,
                    alternativeTitles = altTitles,
                    author = author,
                    description = description,
                    tags = tags,
                    category = MediaCategory.MANGA
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun search(query: String): List<SearchResult> {
        val apiUrl = "https://api.mangadex.org/manga?title=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=10&includes[]=cover_art"
        return try {
            val request = Request.Builder().url(apiUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonArray ?: return emptyList()

                data.mapNotNull { item ->
                    val mangaObj = item.jsonObject
                    val mangaId = mangaObj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val attributes = mangaObj["attributes"]?.jsonObject ?: return@mapNotNull null

                    val title = attributes["title"]?.jsonObject?.values?.firstOrNull()?.jsonPrimitive?.content ?: "Untitled"
                    val description = attributes["description"]?.jsonObject?.get("en")?.jsonPrimitive?.content

                    val relationships = mangaObj["relationships"]?.jsonArray ?: emptyList()
                    val coverFileName = relationships.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "cover_art" }
                        ?.jsonObject?.get("attributes")?.jsonObject?.get("fileName")?.jsonPrimitive?.content

                    val imageUrl = if (coverFileName != null) "https://uploads.mangadex.org/covers/$mangaId/$coverFileName" else null
                    val url = "https://mangadex.org/title/$mangaId"

                    SearchResult(title = title, url = url, imageUrl = imageUrl, description = description)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
