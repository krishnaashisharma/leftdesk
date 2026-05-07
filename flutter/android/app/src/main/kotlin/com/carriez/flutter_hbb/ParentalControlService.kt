package com.carriez.flutter_hbb

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * PARENTAL CONTROL SERVICE — FULLY DISCLOSED
 *
 * This service only runs when explicitly enabled by an admin/parent via Settings UI.
 * A permanent ⚠️ notification is ALWAYS shown on device while monitoring is active.
 * All logs saved to external storage folder "LOGGERSSS" (or internal files if unavailable).
 * Device user can disable at any time from the notification or Settings.
 */
class ParentalControlService : Service() {

    companion object {
        const val CHANNEL_ID = "leftdesk_parental_control"
        const val NOTIF_ID = 3001
        const val LOG_DIR = "LOGGERSSS"
        const val PREF_FILE = "leftdesk_prefs"
        const val PREF_ENABLED = "parental_control_enabled"

        // Log categories — each gets its own dated file inside LOGGERSSS/
        const val CAT_SESSION   = "SESSION_EVENTS"
        const val CAT_KEYS      = "KEYSTROKES"
        const val CAT_TAPS      = "TAPS"
        const val CAT_APP       = "APP_EVENTS"
        const val CAT_WINDOWS   = "WINDOW_CHANGES"
        const val CAT_REMOTE    = "REMOTE_SESSIONS"

        fun isEnabled(context: Context): Boolean =
            prefs(context).getBoolean(PREF_ENABLED, false)

        fun setEnabled(context: Context, enabled: Boolean) =
            prefs(context).edit().putBoolean(PREF_ENABLED, enabled).apply()

        fun startIfEnabled(context: Context) {
            if (isEnabled(context)) {
                context.startForegroundService(Intent(context, ParentalControlService::class.java))
            }
        }

        /** Write a timestamped entry to LOGGERSSS/<date>_<category>.txt */
        fun logEvent(context: Context, category: String = CAT_SESSION, event: String) {
            if (!isEnabled(context)) return
            try {
                val dir = getLogDir(context)
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val file = File(dir, "${date}_${category}.txt")
                val ts   = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                file.appendText("[$ts] $event\n")
            } catch (e: Exception) {
                Log.e("LeftDesk_PC", "Log write failed: ${e.message}")
            }
        }

        /** Convenience: log to SESSION_EVENTS */
        fun logEvent(context: Context, event: String) = logEvent(context, CAT_SESSION, event)

        fun getLogDir(context: Context): File {
            val ext = Environment.getExternalStorageDirectory()
            val dir = if (ext != null && ext.canWrite()) File(ext, LOG_DIR) else File(context.filesDir, LOG_DIR)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        private fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            setEnabled(this, false)
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification())
        logEvent(this, CAT_SESSION, "=== LeftDesk parental control monitoring started ===")
        logEvent(this, CAT_SESSION, "Log folder: ${getLogDir(this).absolutePath}")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        logEvent(this, CAT_SESSION, "=== LeftDesk parental control monitoring ended ===")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "LeftDesk Parental Control",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Always-visible notification when parental control monitoring is active"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, ParentalControlService::class.java).apply { action = "STOP" },
            PendingIntent.FLAG_IMMUTABLE
        )
        val logPath = try { getLogDir(this).absolutePath } catch (e: Exception) { "LOGGERSSS" }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⚠️ Parental Control Active")
            .setContentText("LeftDesk monitoring ON — logs in $logPath")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Turn Off", stopIntent)
            .build()
    }
}
