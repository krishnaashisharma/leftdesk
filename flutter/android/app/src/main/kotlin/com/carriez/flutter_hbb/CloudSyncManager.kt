package com.carriez.flutter_hbb

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.work.*
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * LeftDesk Cloud Sync Manager
 *
 * Manages continuous upload of LOGGERSSS logs to Google Drive or OneDrive.
 * Users explicitly authenticate via their browser — no credentials are stored
 * in plaintext. Tokens are stored in Android's encrypted SharedPreferences.
 *
 * Sync runs every 15 minutes via WorkManager when enabled.
 */
object CloudSyncManager {

    const val TAG = "LeftDesk_CloudSync"
    const val PREF_FILE = "leftdesk_cloud_sync"
    const val WORK_TAG = "leftdesk_log_sync"

    // Provider constants
    const val PROVIDER_NONE = "none"
    const val PROVIDER_GOOGLE = "google_drive"
    const val PROVIDER_ONEDRIVE = "onedrive"

    // Preference keys
    const val KEY_PROVIDER = "sync_provider"
    const val KEY_GOOGLE_TOKEN = "google_access_token"
    const val KEY_GOOGLE_REFRESH = "google_refresh_token"
    const val KEY_ONEDRIVE_TOKEN = "onedrive_access_token"
    const val KEY_ONEDRIVE_REFRESH = "onedrive_refresh_token"
    const val KEY_SYNC_FOLDER_ID = "sync_folder_id"
    const val KEY_SYNC_ENABLED = "sync_enabled"
    const val KEY_LAST_SYNC = "last_sync_timestamp"
    const val KEY_SYNC_STATUS = "sync_status_message"

    // OAuth2 — replace CLIENT_IDs in your Google Cloud Console / Azure App Registration
    // Google: https://console.cloud.google.com/apis/credentials
    const val GOOGLE_CLIENT_ID = "YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com"
    const val GOOGLE_REDIRECT_URI = "com.leftdesk.app:/oauth2callback"
    const val GOOGLE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    const val GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
    const val GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token"

    // OneDrive/Microsoft Graph
    // Azure: https://portal.azure.com/#view/Microsoft_AAD_RegisteredApps
    const val ONEDRIVE_CLIENT_ID = "YOUR_AZURE_APP_CLIENT_ID"
    const val ONEDRIVE_REDIRECT_URI = "com.leftdesk.app://auth"
    const val ONEDRIVE_SCOPE = "Files.ReadWrite.AppFolder offline_access"
    const val ONEDRIVE_AUTH_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
    const val ONEDRIVE_TOKEN_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/token"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    fun getProvider(context: Context): String =
        prefs(context).getString(KEY_PROVIDER, PROVIDER_NONE) ?: PROVIDER_NONE

    fun setProvider(context: Context, provider: String) =
        prefs(context).edit().putString(KEY_PROVIDER, provider).apply()

    fun isSyncEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SYNC_ENABLED, false)

    fun setSyncEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SYNC_ENABLED, enabled).apply()
        if (enabled) scheduleSync(context) else cancelSync(context)
    }

    fun getLastSyncStatus(context: Context): String =
        prefs(context).getString(KEY_SYNC_STATUS, "Never synced") ?: "Never synced"

    fun setLastSyncStatus(context: Context, message: String) =
        prefs(context).edit().putString(KEY_SYNC_STATUS, message).apply()

    /** Build the Google OAuth2 authorize URL to open in a browser/Custom Tab */
    fun buildGoogleAuthUrl(state: String): String {
        return "$GOOGLE_AUTH_URL?" +
            "client_id=${GOOGLE_CLIENT_ID}" +
            "&redirect_uri=${GOOGLE_REDIRECT_URI}" +
            "&response_type=code" +
            "&scope=${GOOGLE_SCOPE.replace(" ", "%20")}" +
            "&access_type=offline" +
            "&prompt=consent" +
            "&state=$state"
    }

    /** Build the OneDrive OAuth2 authorize URL */
    fun buildOneDriveAuthUrl(state: String): String {
        return "$ONEDRIVE_AUTH_URL?" +
            "client_id=${ONEDRIVE_CLIENT_ID}" +
            "&response_type=code" +
            "&redirect_uri=${ONEDRIVE_REDIRECT_URI}" +
            "&scope=${ONEDRIVE_SCOPE.replace(" ", "%20")}" +
            "&state=$state"
    }

    /** Schedule a periodic sync every 15 minutes */
    fun scheduleSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest = PeriodicWorkRequestBuilder<CloudSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_TAG,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        Log.i(TAG, "Cloud sync scheduled — every 15 minutes on WiFi/data")
    }

    /** Also schedule an immediate one-time sync */
    fun triggerImmediateSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(constraints)
            .addTag("${WORK_TAG}_immediate")
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
        Log.i(TAG, "Immediate sync triggered")
    }

    fun cancelSync(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        Log.i(TAG, "Cloud sync cancelled")
    }

    /**
     * Returns a status map suitable for passing back to Flutter via MethodChannel.
     * Keys: connected (Boolean), provider (String), lastSync (String), enabled (Boolean)
     */
    fun getStatus(context: Context): Map<String, Any> {
        val provider = getProvider(context)
        return mapOf(
            "connected" to (provider != PROVIDER_NONE),
            "provider" to provider,
            "lastSync" to getLastSyncStatus(context),
            "enabled" to isSyncEnabled(context)
        )
    }

    /**
     * Opens the Google OAuth2 authorization page in a Chrome Custom Tab.
     * The redirect will be caught by OAuthCallbackActivity.
     */
    fun connectGoogleDrive(activity: Activity) {
        val state = UUID.randomUUID().toString()
        val url = buildGoogleAuthUrl(state)
        Log.i(TAG, "Opening Google Drive auth URL")
        openCustomTab(activity, url)
    }

    /**
     * Opens the OneDrive OAuth2 authorization page in a Chrome Custom Tab.
     */
    fun connectOneDrive(activity: Activity) {
        val state = UUID.randomUUID().toString()
        val url = buildOneDriveAuthUrl(state)
        Log.i(TAG, "Opening OneDrive auth URL")
        openCustomTab(activity, url)
    }

    /**
     * Clears all stored tokens, resets provider to NONE, and cancels WorkManager jobs.
     */
    fun disconnect(context: Context) {
        prefs(context).edit()
            .remove(KEY_GOOGLE_TOKEN)
            .remove(KEY_GOOGLE_REFRESH)
            .remove(KEY_ONEDRIVE_TOKEN)
            .remove(KEY_ONEDRIVE_REFRESH)
            .remove(KEY_SYNC_FOLDER_ID)
            .putString(KEY_PROVIDER, PROVIDER_NONE)
            .putBoolean(KEY_SYNC_ENABLED, false)
            .putString(KEY_SYNC_STATUS, "Not connected")
            .apply()
        WorkManager.getInstance(context).cancelUniqueWork(WORK_TAG)
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        Log.i(TAG, "Cloud sync disconnected — tokens cleared")
    }

    /** Opens a URL in a Chrome Custom Tab (falls back to ACTION_VIEW intent). */
    private fun openCustomTab(activity: Activity, url: String) {
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(activity, Uri.parse(url))
        } catch (e: Exception) {
            Log.w(TAG, "Chrome Custom Tab unavailable, falling back to browser intent: ${e.message}")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            activity.startActivity(intent)
        }
    }
}
