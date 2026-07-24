package app.lazydex.ui.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.lazydex.MainActivity
import app.lazydex.data.anilist.AnilistSyncManager
import app.lazydex.data.anilist.AnilistTokenStore
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class TrackLoginActivity : ComponentActivity() {

    private val tokenStore: AnilistTokenStore by inject()
    private val syncManager: AnilistSyncManager by inject()

    companion object {
        private const val TAG = "TrackLoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: run {
            returnToSettings()
            return
        }

        val rawDataString = intent.dataString ?: uri.toString()
        val dataString = when {
            !uri.encodedQuery.isNullOrBlank() -> uri.encodedQuery
            !uri.encodedFragment.isNullOrBlank() -> uri.encodedFragment
            else -> extractFragmentFromRawUrl(rawDataString)
        }

        val params = dataString
            ?.split("&")
            ?.filter { it.isNotBlank() }
            ?.associate { param ->
                val parts = param.split("=", limit = 2).map(Uri::decode)
                parts[0] to (parts.getOrNull(1) ?: "")
            }
            .orEmpty()

        val accessToken = params["access_token"]
        val expiresIn = params["expires_in"]?.toLongOrNull() ?: 31536000L

        if (!accessToken.isNullOrBlank()) {
            lifecycleScope.launch {
                try {
                    syncManager.loginWithToken(accessToken, expiresIn)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to complete login with token", e)
                }
                returnToSettings()
            }
        } else {
            returnToSettings()
        }
    }

    private fun extractFragmentFromRawUrl(url: String): String? {
        val hashIndex = url.indexOf('#')
        if (hashIndex == -1) return null
        return url.substring(hashIndex + 1)
    }

    private fun returnToSettings() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }
}
