package app.lazydex.data.anilist

import org.junit.Assert.assertEquals
import org.junit.Test

class AnilistSyncConflictTest {

    @Test
    fun resolveSyncConflict_resolvesKeepLocalWhenWithin60sClockSkewBuffer() {
        val now = 1000000L
        val localTime = now
        val remoteTime = now + 30000L // 30s diff

        val decision = AnilistSyncManager.resolveSyncConflict(localTime, remoteTime)
        assertEquals(SyncDecision.KeepLocal, decision)
    }

    @Test
    fun resolveSyncConflict_resolvesPullFromRemoteWhenRemoteIsStrictlyNewerBeyond60s() {
        val now = 1000000L
        val localTime = now
        val remoteTime = now + 120000L // 2 mins diff

        val decision = AnilistSyncManager.resolveSyncConflict(localTime, remoteTime)
        assertEquals(SyncDecision.PullFromRemote, decision)
    }

    @Test
    fun resolveSyncConflict_resolvesPushToRemoteWhenLocalIsStrictlyNewerBeyond60s() {
        val now = 1000000L
        val localTime = now + 120000L // 2 mins diff
        val remoteTime = now

        val decision = AnilistSyncManager.resolveSyncConflict(localTime, remoteTime)
        assertEquals(SyncDecision.PushToRemote, decision)
    }
}
