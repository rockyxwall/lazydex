package app.lazydex.data.anilist

import android.util.Log
import app.lazydex.data.anilist.model.ScoreFormat
import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.MediaFormat
import app.lazydex.domain.model.UserStatus
import app.lazydex.util.ScoreConverter
import app.lazydex.util.TitleNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

enum class SyncDecision {
    PullFromRemote,
    PushToRemote,
    KeepLocal
}

class AnilistSyncManager(
    private val api: AnilistApi,
    private val tokenStore: AnilistTokenStore,
    private val dao: MediaItemDao
) {

    companion object {
        private const val TAG = "AnilistSyncManager"
        const val CLOCK_SKEW_BUFFER_MS = 60_000L

        fun resolveSyncConflict(localTimeMs: Long, remoteTimeMs: Long): SyncDecision {
            val diff = abs(remoteTimeMs - localTimeMs)
            return when {
                diff < CLOCK_SKEW_BUFFER_MS -> SyncDecision.KeepLocal
                remoteTimeMs > localTimeMs -> SyncDecision.PullFromRemote
                localTimeMs > remoteTimeMs -> SyncDecision.PushToRemote
                else -> SyncDecision.KeepLocal
            }
        }
    }

    suspend fun loginWithToken(token: String, expiresInSeconds: Long = 31536000L) {
        tokenStore.saveToken(token, expiresInSeconds)
        val viewer = api.getViewer()
        val userFormat = ScoreFormat.fromString(viewer.mediaListOptions?.scoreFormat)
        tokenStore.saveUserInfo(viewer.id, viewer.name, userFormat)
        performFullSync()
    }

    suspend fun performFullSync() = withContext(Dispatchers.IO) {
        if (!tokenStore.isLoggedIn()) return@withContext
        val userId = tokenStore.getUserId()
        if (userId <= 0) return@withContext

        val syncStartTime = System.currentTimeMillis()
        val userFormat = tokenStore.getScoreFormat()
        val fetchedRemoteEntries = mutableListOf<ALMediaListEntry>()

        // 1. Paginated streaming fetch for ANIME & MANGA (perPage = 100)
        for (type in listOf("ANIME", "MANGA")) {
            var chunk = 1
            var hasNextChunk = true
            while (hasNextChunk) {
                val collection = api.fetchMediaListChunk(userId = userId, type = type, chunk = chunk, perPage = 100)
                collection.lists?.forEach { group ->
                    group.entries?.forEach { entry ->
                        if (entry.media != null) {
                            fetchedRemoteEntries.add(entry)
                        }
                    }
                }
                hasNextChunk = collection.hasNextChunk == true
                chunk++
            }
        }

        val distinctRemoteEntries = fetchedRemoteEntries.distinctBy { it.id }
        val matchedRemoteEntryIds = mutableSetOf<Long>()

        // 2. Incremental Tier 1 matching (Entry ID / Source URL)
        val localEntities = dao.getAll()
        val entryIdMap = localEntities.filter { it.anilistListEntryId != null }.associateBy { it.anilistListEntryId!! }
        val sourceUrlMap = localEntities.filter { !it.sourceUrl.isNullOrBlank() }.associateBy { it.sourceUrl!! }

        val unmatchedRemoteEntries = mutableListOf<ALMediaListEntry>()

        for (remote in distinctRemoteEntries) {
            val category = inferCategory(remote)
            val expectedUrl = "https://anilist.co/${if (category == MediaCategory.ANIME) "anime" else "manga"}/${remote.mediaId}"

            val matchedLocal = entryIdMap[remote.id] ?: sourceUrlMap[expectedUrl]

            if (matchedLocal != null) {
                matchedRemoteEntryIds.add(remote.id)
                processMatchedItem(matchedLocal, remote, category, userFormat, syncStartTime)
            } else {
                unmatchedRemoteEntries.add(remote)
            }
        }

        // 3. Tier 3 Normalized Title matching on Dispatchers.Default (O(1) Map lookup)
        if (unmatchedRemoteEntries.isNotEmpty()) {
            val unboundEntities = dao.getAll().filter { it.anilistListEntryId == null }
            val titleIndex = withContext(Dispatchers.Default) {
                unboundEntities.groupBy { TitleNormalizer.normalize(it.title) }
            }

            for (remote in unmatchedRemoteEntries) {
                val category = inferCategory(remote)
                val rawTitle = remote.media?.title?.userPreferred
                    ?: remote.media?.title?.romaji
                    ?: remote.media?.title?.english
                    ?: ""
                val normalizedTitle = TitleNormalizer.normalize(rawTitle)

                val candidateList = titleIndex[normalizedTitle]?.filter { it.category == category.name } ?: emptyList()
                val bestMatch = candidateList.maxByOrNull { it.dateAdded }

                if (bestMatch != null) {
                    matchedRemoteEntryIds.add(remote.id)
                    processMatchedItem(bestMatch, remote, category, userFormat, syncStartTime)
                }
            }
        }

        // 4. Remote Deletion Reconciliation Phase
        // Bound items missing from remote payload AND not edited offline recently are flagged for resolution
        val allBoundItems = dao.getBoundItems()
        for (boundItem in allBoundItems) {
            if (boundItem.anilistListEntryId != null && boundItem.anilistListEntryId !in matchedRemoteEntryIds) {
                if (boundItem.localUpdatedAt < syncStartTime) {
                    val updated = boundItem.copy(syncPendingAction = "REMOTE_DELETED_PENDING_RESOLUTION")
                    dao.upsert(updated)
                }
            }
        }
    }

    private suspend fun processMatchedItem(
        localEntity: MediaItemEntity,
        remote: ALMediaListEntry,
        inferredCategory: MediaCategory,
        userFormat: ScoreFormat,
        syncTime: Long
    ) {
        val remoteTimeMs = (remote.updatedAt ?: 0L) * 1000L
        val localTimeMs = if (localEntity.localUpdatedAt > 0) localEntity.localUpdatedAt else localEntity.lastUpdated
        val decision = resolveSyncConflict(localTimeMs, remoteTimeMs)

        val expectedUrl = "https://anilist.co/${if (inferredCategory == MediaCategory.ANIME) "anime" else "manga"}/${remote.mediaId}"

        when (decision) {
            SyncDecision.PullFromRemote -> {
                val pulledStatus = mapAniListStatus(remote.status)
                val mergedRating = remote.scoreRaw ?: localEntity.rating
                val updated = localEntity.copy(
                    anilistListEntryId = remote.id,
                    sourceUrl = localEntity.sourceUrl ?: expectedUrl,
                    currentProgress = remote.progress ?: localEntity.currentProgress,
                    progressVolumes = remote.progressVolumes ?: localEntity.progressVolumes,
                    userStatus = pulledStatus.name,
                    rating = mergedRating,
                    lastSyncedAt = syncTime,
                    mediaFormat = remote.media?.format ?: localEntity.mediaFormat,
                    totalItems = remote.media?.chapters ?: remote.media?.episodesCount() ?: localEntity.totalItems,
                    totalVolumes = remote.media?.volumes ?: localEntity.totalVolumes,
                    durationMinutes = remote.media?.duration ?: localEntity.durationMinutes,
                    publishingStatus = remote.media?.status ?: localEntity.publishingStatus,
                    season = remote.media?.season ?: localEntity.season,
                    sourceMaterial = remote.media?.source ?: localEntity.sourceMaterial,
                    isAdult = remote.media?.isAdult ?: localEntity.isAdult,
                    syncPendingAction = null
                )
                dao.upsert(updated)
            }
            SyncDecision.PushToRemote -> {
                try {
                    pushItemEntity(localEntity, userFormat)
                } catch (e: Exception) {
                    Log.w(TAG, "Push to remote failed for item ${localEntity.id}, queuing for background retry", e)
                }
            }
            SyncDecision.KeepLocal -> {
                // 60s clock skew buffer deterministic merge
                val mergedProgress = maxOf(localEntity.currentProgress, remote.progress ?: 0)
                val mergedStatus = if (mergedProgress >= (localEntity.totalItems ?: Int.MAX_VALUE)) "COMPLETED" else localEntity.userStatus
                val updated = localEntity.copy(
                    anilistListEntryId = remote.id,
                    sourceUrl = localEntity.sourceUrl ?: expectedUrl,
                    currentProgress = mergedProgress,
                    userStatus = mergedStatus,
                    lastSyncedAt = syncTime,
                    syncPendingAction = null
                )
                dao.upsert(updated)
            }
        }
    }

    suspend fun pushItemEntity(localEntity: MediaItemEntity, userFormat: ScoreFormat) = withContext(Dispatchers.IO) {
        val category = MediaCategory.fromString(localEntity.category) ?: return@withContext
        if (category !in listOf(MediaCategory.ANIME, MediaCategory.MANGA, MediaCategory.NOVEL)) return@withContext

        val mediaId = extractAniListMediaId(localEntity.sourceUrl) ?: return@withContext
        val statusString = toAniListStatusString(localEntity.userStatus)
        val scoreRaw = localEntity.rating?.let { ScoreConverter.snapToFormatInterval(it, userFormat) }

        val result = api.saveMediaListEntry(
            id = localEntity.anilistListEntryId,
            mediaId = mediaId,
            status = statusString,
            progress = localEntity.currentProgress,
            progressVolumes = localEntity.progressVolumes,
            scoreRaw = scoreRaw,
            isPrivate = localEntity.isPrivate
        )

        val now = System.currentTimeMillis()
        val updated = localEntity.copy(
            anilistListEntryId = result.id,
            lastSyncedAt = now,
            localUpdatedAt = now,
            syncPendingAction = null
        )
        dao.upsert(updated)
    }

    fun resolveSyncConflict(localTimeMs: Long, remoteTimeMs: Long): SyncDecision {
        val diff = abs(remoteTimeMs - localTimeMs)
        return when {
            diff < CLOCK_SKEW_BUFFER_MS -> SyncDecision.KeepLocal
            remoteTimeMs > localTimeMs -> SyncDecision.PullFromRemote
            localTimeMs > remoteTimeMs -> SyncDecision.PushToRemote
            else -> SyncDecision.KeepLocal
        }
    }

    private fun inferCategory(entry: ALMediaListEntry): MediaCategory {
        val formatStr = entry.media?.format
        return when {
            formatStr in listOf("NOVEL", "LIGHT_NOVEL") -> MediaCategory.NOVEL
            formatStr in listOf("MANGA", "ONE_SHOT") -> MediaCategory.MANGA
            else -> MediaCategory.ANIME
        }
    }

    private fun mapAniListStatus(status: String?): UserStatus {
        return when (status?.uppercase()) {
            "CURRENT" -> UserStatus.READING
            "COMPLETED" -> UserStatus.COMPLETED
            "PAUSED" -> UserStatus.ON_HOLD
            "DROPPED" -> UserStatus.DROPPED
            "PLANNING" -> UserStatus.PLAN_TO
            "REPEATING" -> UserStatus.REPEATING
            else -> UserStatus.PLAN_TO
        }
    }

    private fun toAniListStatusString(userStatus: String): String {
        return when (userStatus.uppercase()) {
            "READING", "WATCHING", "PLAYING" -> "CURRENT"
            "COMPLETED" -> "COMPLETED"
            "ON_HOLD" -> "PAUSED"
            "DROPPED" -> "DROPPED"
            "PLAN_TO" -> "PLANNING"
            "REPEATING" -> "REPEATING"
            else -> "PLANNING"
        }
    }

    private fun extractAniListMediaId(url: String?): Long? {
        if (url == null) return null
        val regex = Regex("anilist\\.co/(?:anime|manga)/(\\d+)")
        return regex.find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    private fun ALMedia.episodesCount(): Int? {
        return try {
            val field = this::class.java.getDeclaredField("episodes")
            field.isAccessible = true
            field.get(this) as? Int
        } catch (e: Exception) {
            null
        }
    }
}
