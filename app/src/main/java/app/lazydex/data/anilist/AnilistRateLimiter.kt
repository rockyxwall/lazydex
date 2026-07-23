package app.lazydex.data.anilist

import android.content.Context
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class AnilistRateLimitException(val retryAfterSeconds: Long) : 
    IOException("AniList Rate Limit Exceeded. Retry after $retryAfterSeconds seconds.")

class AnilistRateLimiter(private val context: Context) : Interceptor {

    private val requestCount = AtomicInteger(0)
    private var windowStartMs = System.currentTimeMillis()

    companion object {
        private const val MAX_REQUESTS_PER_MINUTE = 85
        private const val ONE_MINUTE_MS = 60_000L
        private const val PREFS_NAME = "anilist_rate_limit_prefs"
        private const val KEY_RESET_TIME = "anilist_rate_limit_reset"

        fun getResetTime(context: Context): Long {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_RESET_TIME, 0L)
        }

        fun setResetTime(context: Context, resetTimeMs: Long) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_RESET_TIME, resetTimeMs)
                .apply()
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val now = System.currentTimeMillis()

        // Check persistent reset time
        val resetTime = getResetTime(context)
        if (now < resetTime) {
            val waitSeconds = ((resetTime - now) / 1000L).coerceAtLeast(1)
            throw AnilistRateLimitException(waitSeconds)
        }

        // Local token bucket check
        synchronized(this) {
            if (now - windowStartMs >= ONE_MINUTE_MS) {
                windowStartMs = now
                requestCount.set(0)
            }

            if (requestCount.get() >= MAX_REQUESTS_PER_MINUTE) {
                val waitSeconds = ((ONE_MINUTE_MS - (now - windowStartMs)) / 1000L).coerceAtLeast(1)
                throw AnilistRateLimitException(waitSeconds)
            }
            requestCount.incrementAndGet()
        }

        val response = chain.proceed(chain.request())

        if (response.code == 429) {
            val retryHeader = response.header("Retry-After")
            val retryAfterSeconds = retryHeader?.toLongOrNull() ?: 60L
            val newResetMs = System.currentTimeMillis() + (retryAfterSeconds * 1000L)
            setResetTime(context, newResetMs)
            throw AnilistRateLimitException(retryAfterSeconds)
        }

        return response
    }
}
