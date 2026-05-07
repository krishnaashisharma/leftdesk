package com.carriez.flutter_hbb

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Background worker that uploads LOGGERSSS log files to the configured cloud provider.
 * Runs every 15 minutes when connected to network.
 * Uses OkHttp (available via RustDesk/LeftDesk dependencies).
 */
class CloudSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val prefs = CloudSyncManager.prefs(applicationContext)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val provider = CloudSyncManager.getProvider(applicationContext)
        val logDir = ParentalControlService.getLogDir(applicationContext)

        if (!CloudSyncManager.isSyncEnabled(applicationContext)) {
            return@withContext Result.success()
        }

        if (!logDir.exists() || logDir.listFiles().isNullOrEmpty()) {
            CloudSyncManager.setLastSyncStatus(applicationContext, "No logs to sync")
            return@withContext Result.success()
        }

        return@withContext try {
            when (provider) {
                CloudSyncManager.PROVIDER_GOOGLE -> syncToGoogleDrive(logDir)
                CloudSyncManager.PROVIDER_ONEDRIVE -> syncToOneDrive(logDir)
                else -> {
                    CloudSyncManager.setLastSyncStatus(applicationContext, "No cloud provider configured")
                    Result.success()
                }
            }
        } catch (e: Exception) {
            Log.e(CloudSyncManager.TAG, "Sync failed: ${e.message}", e)
            val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            CloudSyncManager.setLastSyncStatus(applicationContext, "[$ts] Error: ${e.message?.take(80)}")
            Result.retry()
        }
    }

    // ─── Google Drive ─────────────────────────────────────────────────────────

    private suspend fun syncToGoogleDrive(logDir: File): Result {
        val accessToken = prefs.getString(CloudSyncManager.KEY_GOOGLE_TOKEN, null)
            ?: return failWithStatus("Google Drive: not authenticated")

        // Ensure folder exists in Drive
        val folderId = getOrCreateGoogleFolder(accessToken)
            ?: return failWithStatus("Google Drive: could not create LOGGERSSS folder")

        var uploaded = 0
        logDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".txt")) {
                uploadFileToGoogleDrive(accessToken, folderId, file)
                uploaded++
            }
        }

        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        CloudSyncManager.setLastSyncStatus(applicationContext, "Google Drive ✓ $uploaded files — $ts")
        Log.i(CloudSyncManager.TAG, "Google Drive sync complete: $uploaded files")
        return Result.success()
    }

    private suspend fun getOrCreateGoogleFolder(token: String): String? {
        val cachedId = prefs.getString(CloudSyncManager.KEY_SYNC_FOLDER_ID, null)
        if (!cachedId.isNullOrBlank()) return cachedId

        // Search for existing folder
        val searchReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?q=name='LOGGERSSS' and mimeType='application/vnd.google-apps.folder' and trashed=false&fields=files(id,name)")
            .header("Authorization", "Bearer $token")
            .build()
        val searchBody = httpClient.newCall(searchReq).execute().use { it.body?.string() }
        val files = searchBody?.let { JSONObject(it).optJSONArray("files") }
        if (files != null && files.length() > 0) {
            val id = files.getJSONObject(0).getString("id")
            prefs.edit().putString(CloudSyncManager.KEY_SYNC_FOLDER_ID, id).apply()
            return id
        }

        // Create folder
        val meta = """{"name":"LOGGERSSS","mimeType":"application/vnd.google-apps.folder"}"""
        val createReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files")
            .header("Authorization", "Bearer $token")
            .post(meta.toRequestBody("application/json".toMediaType()))
            .build()
        val createBody = httpClient.newCall(createReq).execute().use { it.body?.string() }
        val folderId = createBody?.let { JSONObject(it).optString("id") }
        if (!folderId.isNullOrBlank()) {
            prefs.edit().putString(CloudSyncManager.KEY_SYNC_FOLDER_ID, folderId).apply()
        }
        return folderId
    }

    private suspend fun uploadFileToGoogleDrive(token: String, folderId: String, file: File) {
        // Check if file already exists (update) or needs to be created (upload)
        val searchReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?q=name='${file.name}' and '${folderId}' in parents and trashed=false&fields=files(id)")
            .header("Authorization", "Bearer $token")
            .build()
        val searchBody = httpClient.newCall(searchReq).execute().use { it.body?.string() }
        val existingId = searchBody?.let {
            val arr = JSONObject(it).optJSONArray("files")
            if (arr != null && arr.length() > 0) arr.getJSONObject(0).getString("id") else null
        }

        if (existingId != null) {
            // Update existing file
            val req = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files/$existingId?uploadType=media")
                .header("Authorization", "Bearer $token")
                .patch(file.asRequestBody("text/plain".toMediaType()))
                .build()
            httpClient.newCall(req).execute().close()
        } else {
            // Multipart upload with metadata
            val meta = """{"name":"${file.name}","parents":["$folderId"]}"""
            val body = MultipartBody.Builder()
                .setType(MultipartBody.MIXED)
                .addPart(meta.toRequestBody("application/json".toMediaType()))
                .addPart(file.asRequestBody("text/plain".toMediaType()))
                .build()
            val req = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
            httpClient.newCall(req).execute().close()
        }
    }

    // ─── OneDrive ─────────────────────────────────────────────────────────────

    private suspend fun syncToOneDrive(logDir: File): Result {
        val accessToken = prefs.getString(CloudSyncManager.KEY_ONEDRIVE_TOKEN, null)
            ?: return failWithStatus("OneDrive: not authenticated")

        // Ensure LOGGERSSS folder exists in OneDrive/Apps/LeftDesk
        ensureOneDriveFolder(accessToken)

        var uploaded = 0
        logDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".txt")) {
                uploadFileToOneDrive(accessToken, file)
                uploaded++
            }
        }

        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        CloudSyncManager.setLastSyncStatus(applicationContext, "OneDrive ✓ $uploaded files — $ts")
        Log.i(CloudSyncManager.TAG, "OneDrive sync complete: $uploaded files")
        return Result.success()
    }

    private suspend fun ensureOneDriveFolder(token: String) {
        val url = "https://graph.microsoft.com/v1.0/me/drive/special/approot:/LOGGERSSS"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        val resp = httpClient.newCall(req).execute()
        if (resp.code == 404) {
            // Create folder
            val body = """{"name":"LOGGERSSS","folder":{},"@microsoft.graph.conflictBehavior":"replace"}"""
            val createReq = Request.Builder()
                .url("https://graph.microsoft.com/v1.0/me/drive/special/approot/children")
                .header("Authorization", "Bearer $token")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(createReq).execute().close()
        }
        resp.close()
    }

    private suspend fun uploadFileToOneDrive(token: String, file: File) {
        // OneDrive simple upload (< 4MB — log files are tiny)
        val url = "https://graph.microsoft.com/v1.0/me/drive/special/approot:/LOGGERSSS/${file.name}:/content"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .put(file.asRequestBody("text/plain".toMediaType()))
            .build()
        httpClient.newCall(req).execute().close()
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun failWithStatus(msg: String): Result {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        CloudSyncManager.setLastSyncStatus(applicationContext, "[$ts] $msg")
        Log.w(CloudSyncManager.TAG, msg)
        return Result.failure()
    }
}
