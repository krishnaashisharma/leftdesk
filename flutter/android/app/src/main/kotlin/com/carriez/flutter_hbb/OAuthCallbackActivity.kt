package com.carriez.flutter_hbb

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Handles the OAuth2 redirect callback after the user authenticates in their browser.
 * Registered in AndroidManifest as the handler for:
 *   com.leftdesk.app:/oauth2callback  (Google)
 *   com.leftdesk.app://auth           (OneDrive)
 */
class OAuthCallbackActivity : Activity() {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleCallback(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleCallback(it) }
    }

    private fun handleCallback(intent: Intent) {
        val uri = intent.data ?: run { finish(); return }
        val code = uri.getQueryParameter("code") ?: run {
            Log.e(TAG, "OAuth callback missing 'code': $uri")
            sendResult("error", "No authorization code received")
            finish()
            return
        }

        val isGoogle = uri.scheme == "com.leftdesk.app" && uri.host == null &&
                       uri.path?.contains("oauth2callback") == true
        val isOneDrive = uri.scheme == "com.leftdesk.app" && uri.host == "auth"

        scope.launch {
            when {
                isGoogle -> exchangeGoogleCode(code)
                isOneDrive -> exchangeOneDriveCode(code)
                else -> sendResult("error", "Unknown OAuth provider")
            }
            withContext(Dispatchers.Main) { finish() }
        }
    }

    private suspend fun exchangeGoogleCode(code: String) {
        val body = FormBody.Builder()
            .add("code", code)
            .add("client_id", CloudSyncManager.GOOGLE_CLIENT_ID)
            .add("redirect_uri", CloudSyncManager.GOOGLE_REDIRECT_URI)
            .add("grant_type", "authorization_code")
            .build()
        val req = Request.Builder()
            .url(CloudSyncManager.GOOGLE_TOKEN_URL)
            .post(body)
            .build()
        try {
            val resp = http.newCall(req).execute()
            val json = JSONObject(resp.body?.string() ?: "{}")
            val access = json.optString("access_token")
            val refresh = json.optString("refresh_token")
            if (access.isNotBlank()) {
                CloudSyncManager.prefs(this).edit()
                    .putString(CloudSyncManager.KEY_GOOGLE_TOKEN, access)
                    .putString(CloudSyncManager.KEY_GOOGLE_REFRESH, refresh)
                    .putString(CloudSyncManager.KEY_PROVIDER, CloudSyncManager.PROVIDER_GOOGLE)
                    .apply()
                CloudSyncManager.setSyncEnabled(this, true)
                sendResult("success", "Google Drive connected")
                Log.i(TAG, "Google Drive token stored, sync enabled")
            } else {
                sendResult("error", "Google token exchange failed: ${json.optString("error")}")
            }
        } catch (e: Exception) {
            sendResult("error", "Google auth error: ${e.message}")
        }
    }

    private suspend fun exchangeOneDriveCode(code: String) {
        val body = FormBody.Builder()
            .add("code", code)
            .add("client_id", CloudSyncManager.ONEDRIVE_CLIENT_ID)
            .add("redirect_uri", CloudSyncManager.ONEDRIVE_REDIRECT_URI)
            .add("grant_type", "authorization_code")
            .add("scope", CloudSyncManager.ONEDRIVE_SCOPE)
            .build()
        val req = Request.Builder()
            .url(CloudSyncManager.ONEDRIVE_TOKEN_URL)
            .post(body)
            .build()
        try {
            val resp = http.newCall(req).execute()
            val json = JSONObject(resp.body?.string() ?: "{}")
            val access = json.optString("access_token")
            val refresh = json.optString("refresh_token")
            if (access.isNotBlank()) {
                CloudSyncManager.prefs(this).edit()
                    .putString(CloudSyncManager.KEY_ONEDRIVE_TOKEN, access)
                    .putString(CloudSyncManager.KEY_ONEDRIVE_REFRESH, refresh)
                    .putString(CloudSyncManager.KEY_PROVIDER, CloudSyncManager.PROVIDER_ONEDRIVE)
                    .apply()
                CloudSyncManager.setSyncEnabled(this, true)
                sendResult("success", "OneDrive connected")
                Log.i(TAG, "OneDrive token stored, sync enabled")
            } else {
                sendResult("error", "OneDrive token exchange failed: ${json.optString("error")}")
            }
        } catch (e: Exception) {
            sendResult("error", "OneDrive auth error: ${e.message}")
        }
    }

    private fun sendResult(status: String, message: String) {
        sendBroadcast(Intent("com.leftdesk.app.CLOUD_AUTH_RESULT").apply {
            putExtra("status", status)
            putExtra("message", message)
        })
        CloudSyncManager.setLastSyncStatus(this, message)
        Log.i(TAG, "OAuth result: $status — $message")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val TAG = "LeftDesk_OAuthCallback"
    }
}
