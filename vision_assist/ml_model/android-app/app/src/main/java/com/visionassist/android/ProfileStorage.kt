package com.visionassist.android

import android.content.Context

class ProfileStorage(context: Context) {
    private val preferences = context.getSharedPreferences("vision_assist_profile", Context.MODE_PRIVATE)

    fun load(): VisualProfile? {
        val payload = preferences.getString(KEY_PROFILE, null) ?: return null
        return runCatching { VisualProfile.fromJson(payload) }.getOrNull()
    }

    fun save(profile: VisualProfile) {
        preferences.edit().putString(KEY_PROFILE, profile.toJson()).apply()
    }

    fun isCaptureActive(): Boolean = preferences.getBoolean(KEY_CAPTURE_ACTIVE, false)

    fun setCaptureActive(active: Boolean) {
        preferences.edit().putBoolean(KEY_CAPTURE_ACTIVE, active).apply()
    }

    companion object {
        private const val KEY_PROFILE = "profile_json"
        private const val KEY_CAPTURE_ACTIVE = "capture_active"
    }
}
