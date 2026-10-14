package com.visionassist.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.HandlerThread
import android.view.accessibility.AccessibilityEvent
import android.os.Handler
import android.util.Log

class ForegroundAppAccessibilityService : AccessibilityService() {
    private var heartbeatThread: HandlerThread? = null
    private var heartbeatHandler: Handler? = null
    private val heartbeat = object : Runnable {
        override fun run() {
            ProfileStorage(this@ForegroundAppAccessibilityService)
                .setAccessibilityHeartbeat(System.currentTimeMillis())
            heartbeatHandler?.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = EVENT_NOTIFICATION_TIMEOUT_MS
            flags = 0
        }
        startHeartbeat()
        heartbeatHandler?.removeCallbacks(heartbeat)
        heartbeat.run()
        Log.i(TAG, "Foreground app monitoring connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) {
            reportDetectionUnavailable("Android delivered an unreadable foreground-app event.")
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString()?.takeIf(String::isNotBlank)
        if (packageName == null) {
            reportDetectionUnavailable("Could not identify the foreground app.")
            return
        }

        val storage = ProfileStorage(this)
        storage.setAccessibilityHeartbeat(System.currentTimeMillis())
        storage.setLastForegroundPackage(packageName)
        sendGuardEvent(ACTION_FOREGROUND_PACKAGE_CHANGED, packageName)
    }

    override fun onInterrupt() {
        reportDetectionUnavailable("Android interrupted foreground-app detection.")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        reportDetectionUnavailable("Foreground-app detection was disconnected.")
        stopHeartbeat()
        ProfileStorage(this).clearAccessibilityHeartbeat()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopHeartbeat()
        ProfileStorage(this).clearAccessibilityHeartbeat()
        super.onDestroy()
    }

    private fun reportDetectionUnavailable(message: String) {
        heartbeatHandler?.removeCallbacks(heartbeat)
        ProfileStorage(this).clearAccessibilityHeartbeat()
        sendGuardEvent(ACTION_GUARD_UNAVAILABLE, message)
        Log.w(TAG, message)
    }

    private fun sendGuardEvent(action: String, value: String) {
        sendBroadcast(
            android.content.Intent(action)
                .setPackage(packageName)
                .putExtra(EXTRA_GUARD_VALUE, value),
        )
    }

    private fun startHeartbeat() {
        if (heartbeatThread?.isAlive == true) return
        val thread = HandlerThread("VisionAssistAccessibilityHeartbeat").apply { start() }
        heartbeatThread = thread
        heartbeatHandler = Handler(thread.looper)
    }

    private fun stopHeartbeat() {
        heartbeatHandler?.removeCallbacks(heartbeat)
        heartbeatThread?.quitSafely()
        heartbeatHandler = null
        heartbeatThread = null
    }

    companion object {
        private const val TAG = "VisionAssistA11y"
        private const val HEARTBEAT_INTERVAL_MS = 2_000L
        private const val EVENT_NOTIFICATION_TIMEOUT_MS = 100L
        const val ACTION_FOREGROUND_PACKAGE_CHANGED =
            "com.visionassist.android.action.FOREGROUND_PACKAGE_CHANGED"
        const val ACTION_GUARD_UNAVAILABLE = "com.visionassist.android.action.GUARD_UNAVAILABLE"
        const val EXTRA_GUARD_VALUE = "guard_value"
    }
}
