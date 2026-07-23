package app.lazydex.data.anilist

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import app.lazydex.MainActivity
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class AnilistAuthException(message: String) : IOException(message)

class AnilistInterceptor(
    private val context: Context,
    private val tokenStore: AnilistTokenStore
) : Interceptor {

    private val hasTriggeredLogout = AtomicBoolean(false)

    companion object {
        private const val CHANNEL_ID = "anilist_auth_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
            .header("User-Agent", "LazyDex/0.0.3")

        val token = runBlocking { tokenStore.getAccessToken() }
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        val response = chain.proceed(builder.build())

        if (response.code == 401) {
            if (hasTriggeredLogout.compareAndSet(false, true)) {
                runBlocking { tokenStore.clearToken() }
                postAuthExpiredNotification(context)
            }
            throw AnilistAuthException("AniList authentication token is invalid or expired.")
        } else if (response.isSuccessful) {
            hasTriggeredLogout.set(false)
        }

        return response
    }

    private fun postAuthExpiredNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AniList Sync Status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications regarding AniList authentication and sync status"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("AniList Session Expired")
            .setContentText("Your AniList session has expired. Tap to log in again.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Drop gracefully if permission missing
        }
    }
}
