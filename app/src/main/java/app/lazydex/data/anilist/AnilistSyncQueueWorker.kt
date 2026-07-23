package app.lazydex.data.anilist

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.lazydex.data.local.dao.MediaItemDao
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

class AnilistSyncQueueWorker(
    context: Context,
    params: WorkerParameters,
    private val syncManager: AnilistSyncManager,
    private val dao: MediaItemDao,
    private val tokenStore: AnilistTokenStore
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "AnilistSyncQueueWorker"
        const val WORK_NAME = "AnilistSyncQueue"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<AnilistSyncQueueWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }

    override suspend fun doWork(): Result {
        if (!tokenStore.isLoggedIn()) {
            return Result.success()
        }

        val pendingItems = dao.getPendingSyncItems()
        if (pendingItems.isEmpty()) {
            return Result.success()
        }

        val userFormat = tokenStore.getScoreFormat()

        for (item in pendingItems) {
            var itemPushed = false
            var attempts = 0

            while (!itemPushed && attempts < 3) {
                attempts++
                try {
                    syncManager.pushItemEntity(item, userFormat)
                    itemPushed = true
                } catch (e: AnilistRateLimitException) {
                    val retrySeconds = e.retryAfterSeconds
                    if (retrySeconds <= 60L) {
                        Log.w(TAG, "Rate limit hit for short duration ($retrySeconds s), delaying in-worker", e)
                        delay((retrySeconds * 1000L) + 500L)
                    } else {
                        Log.w(TAG, "Rate limit hit for long duration ($retrySeconds s), enqueuing replacement worker with initialDelay", e)
                        val delayedRequest = OneTimeWorkRequestBuilder<AnilistSyncQueueWorker>()
                            .setInitialDelay(retrySeconds, TimeUnit.SECONDS)
                            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                            .build()

                        WorkManager.getInstance(applicationContext)
                            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, delayedRequest)
                        return Result.failure()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to push item ${item.id} to AniList", e)
                    break
                }
            }
        }

        return Result.success()
    }
}
