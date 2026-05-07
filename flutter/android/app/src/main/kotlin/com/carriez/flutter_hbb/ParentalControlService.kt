package com.carriez.flutter_hbb

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * PARENTAL CONTROL SERVICE — FULLY DISCLOSED
 *
 * This service only runs when explicitly enabled by an admin/parent via the Settings UI.
 * A permanent notification is ALWAYS shown on device while monitoring is active.
 * All logged data is stored locally on-device only — never uploaded.
 * Can be disabled at any time from LeftDesk Settings → Parental Control.
 */
class ParentalControlService : Service() {

    companion object {
        const val CHANNEL_ID = "leftdesk_parental_control"
        const val NOTIF_ID = 3001
        const val LOG_FILE = "leftdesk_activity_log.txt"
        const val PREF_FILE = "leftdesk_prefs"
        const val PREF_ENABLED = "parental_control_enabled"

        fun isEnabled(context: Context): Boolean {
            return prefs(context).getBoolean(PREF_ENABLED, false)
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            prefs(context).edit().putBoolean(PREF_ENABLED, enabled).apply()
        }

        fun startIfEnabled(context: Context) {
            if (isEnabled(context)) {
                val intent = Intent(context, ParentalControlService::class.java)
                context.startForegroundService(intent)
            }
        }

        fun logEvent(context: Context, event: String) {
            if (!isEnabled(context)) return
            try {
                val file = File(context.filesDir, LOG_FILE)
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                file.appendText("[$ts] $event\n")
            } catch (e: Exception) {
                Log.e("LeftDesk_PC", "Log write failed: ${e.message}")
            }
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
        logEvent(this, "LeftDesk parental control monitoring session started")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        logEvent(this, "LeftDesk parental control monitoring session ended")
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⚠️ Parental Control Active")
            .setContentText("LeftDesk activity monitoring is ON. Tap 'Turn Off' to disable.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Turn Off", stopIntent)
            .build()
    }
}
