package com.visionassist.android

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AccessibilityGuard {
    const val HEARTBEAT_TIMEOUT_MS = 10_000L

    fun isServiceEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val component = ComponentName(context, ForegroundAppAccessibilityService::class.java)
        return enabledServices.split(':').any {
            ComponentName.unflattenFromString(it) == component
        } &&
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0,
            ) == 1
    }

    fun hasRecentHeartbeat(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val lastHeartbeat = ProfileStorage(context).accessibilityHeartbeat()
        return lastHeartbeat > 0L && nowMillis - lastHeartbeat in 0..HEARTBEAT_TIMEOUT_MS
    }

    fun isAvailable(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean =
        isServiceEnabled(context) && hasRecentHeartbeat(context, nowMillis)
}
