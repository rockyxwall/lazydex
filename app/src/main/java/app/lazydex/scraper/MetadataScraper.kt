package app.lazydex.scraper

import app.lazydex.scraper.source.ScrapedMetadata
import app.lazydex.scraper.source.SourceRegistry
import app.lazydex.util.UrlNormalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import java.io.IOException
import java.net.InetAddress

class SafeDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        for (address in addresses) {
            if (address.isLoopbackAddress || 
                address.isSiteLocalAddress || 
                address.isLinkLocalAddress || 
                address.isAnyLocalAddress) {
                throw IOException("Access to local/private network addresses is blocked")
            }
        }
        return addresses
    }
}

class MetadataScraper(
    private val okHttpClient: OkHttpClient,
    private val sourceRegistry: SourceRegistry
) {

    suspend fun scrape(url: String): Result<ScrapedMetadata> = withTimeout(30_000L) {
        withContext(Dispatchers.IO) {
            try {
                val normalizedUrl = UrlNormalizer.normalize(url)
                if (!validateUrl(normalizedUrl)) {
                    return@withContext Result.failure(IllegalArgumentException("Invalid URL or unsafe format. Only public HTTPS URLs are supported."))
                }

                val metadata = sourceRegistry.scrape(normalizedUrl)
                if (metadata != null && metadata.title.isNotBlank()) {
                    Result.success(metadata)
                } else {
                    Result.failure(IOException("Failed to scrape metadata from URL"))
                }
            } catch (e: TimeoutCancellationException) {
                Result.failure(Exception("Request timed out. The site may be slow or unreachable.", e))
            } catch (e: Exception) {
                if (e is IOException && e.message == "Canceled" || !currentCoroutineContext().isActive) {
                    throw CancellationException("Scrape cancelled", e)
                }
                Result.failure(e)
            }
        }
    }

    private fun validateUrl(url: String): Boolean {
        if (!url.startsWith("https://", ignoreCase = true)) return false
        if (url.length > 2048) return false
        val host = url.toHttpUrlOrNull()?.host ?: return false
        if (host.matches(Regex("""^(\d{1,3}\.){3}\d{1,3}$""")) || host.startsWith("[") && host.endsWith("]")) return false
        return true
    }
}

private const val MAX_SCRAPE_BYTES = 5L * 1024 * 1024

class SizeLimitedSource(delegate: Source, private val maxBytes: Long) : ForwardingSource(delegate) {
    private var totalRead = 0L
    override fun read(sink: Buffer, byteCount: Long): Long {
        val result = super.read(sink, byteCount)
        if (result != -1L) {
            totalRead += result
            if (totalRead > maxBytes) throw IOException("Response size limit of $maxBytes bytes exceeded")
        }
        return result
    }
}
