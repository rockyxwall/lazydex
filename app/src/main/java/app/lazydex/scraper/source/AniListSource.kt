package app.lazydex.scraper.source

import app.lazydex.domain.model.MediaCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AniListSource(private val okHttpClient: OkHttpClient) : Source {
    override val id: String = "anilist"
    override val name: String = "AniList"
    override val baseUrl: String = "https://anilist.co"
    override val urlPattern: Regex = Regex("""https?://(?:www\.)?anilist\.co/(anime|manga)/(\d+).*""")
    override val isSearchable: Boolean = true
    override val categoryHint: MediaCategory? = null

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun scrape(url: String): ScrapedMetadata? {
        val match = urlPattern.find(url) ?: return null
        val typeStr = match.groupValues[1]  // "anime" or "manga"
        val mediaId = match.groupValues[2].toIntOrNull() ?: return null

        val category = if (typeStr.equals("anime", ignoreCase = true)) MediaCategory.ANIME else MediaCategory.MANGA

        val query = """
            query (${'$'}id: Int) {
              Media (id: ${'$'}id) {
                id
                title { romaji english native }
                coverImage { extraLarge large }
                description(asHtml: false)
                genres
                tags { name }
                staff { edges { role node { name { full } } } }
              }
            }
        """.trimIndent()

        val payload = buildJsonObject {
            put("query", query)
            putJsonObject("variables") {
                put("id", mediaId)
            }
        }.toString()

        return try {
            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val root = json.parseToJsonElement(body).jsonObject
                val media = root["data"]?.jsonObject?.get("Media")?.jsonObject ?: return null

                val titleObj = media["title"]?.jsonObject
                val title = titleObj?.get("english")?.jsonPrimitive?.content
                    ?: titleObj?.get("romaji")?.jsonPrimitive?.content
                    ?: titleObj?.get("native")?.jsonPrimitive?.content
                    ?: "Untitled"

                val altTitles = mutableListOf<String>()
                titleObj?.get("romaji")?.jsonPrimitive?.content?.let { if (it != title) altTitles.add(it) }
                titleObj?.get("native")?.jsonPrimitive?.content?.let { if (it != title) altTitles.add(it) }

                val imageUrl = media["coverImage"]?.jsonObject?.get("extraLarge")?.jsonPrimitive?.content
                    ?: media["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.content ?: ""

                val description = media["description"]?.jsonPrimitive?.content ?: ""

                val genres = media["genres"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
                val tags = media["tags"]?.jsonArray?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content } ?: emptyList()

                val staffEdges = media["staff"]?.jsonObject?.get("edges")?.jsonArray
                val author = staffEdges?.firstOrNull {
                    val role = it.jsonObject["role"]?.jsonPrimitive?.content ?: ""
                    role.contains("Original Story", ignoreCase = true) || role.contains("Author", ignoreCase = true) || role.contains("Story", ignoreCase = true)
                }?.jsonObject?.get("node")?.jsonObject?.get("name")?.jsonObject?.get("full")?.jsonPrimitive?.content ?: ""

                ScrapedMetadata(
                    title = title,
                    imageUrl = imageUrl,
                    alternativeTitles = altTitles,
                    author = author,
                    description = description,
                    genres = genres,
                    tags = tags,
                    category = category
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun search(query: String): List<SearchResult> {
        val searchQuery = """
            query (${'$'}search: String) {
              Page (page: 1, perPage: 10) {
                media (search: ${'$'}search) {
                  id
                  type
                  title { romaji english }
                  coverImage { large }
                  description(asHtml: false)
                }
              }
            }
        """.trimIndent()

        val payload = buildJsonObject {
            put("query", searchQuery)
            putJsonObject("variables") {
                put("search", query)
            }
        }.toString()

        return try {
            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val mediaList = root["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray ?: return emptyList()

                mediaList.mapNotNull { item ->
                    val mediaObj = item.jsonObject
                    val id = mediaObj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val type = mediaObj["type"]?.jsonPrimitive?.content?.lowercase() ?: "anime"
                    val titleObj = mediaObj["title"]?.jsonObject
                    val title = titleObj?.get("english")?.jsonPrimitive?.content
                        ?: titleObj?.get("romaji")?.jsonPrimitive?.content ?: "Untitled"
                    val imageUrl = mediaObj["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.content
                    val description = mediaObj["description"]?.jsonPrimitive?.content
                    val url = "https://anilist.co/$type/$id"

                    SearchResult(title = title, url = url, imageUrl = imageUrl, description = description)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
