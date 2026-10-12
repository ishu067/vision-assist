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

    companion object {
        private const val KEY_PROFILE = "profile_json"
    }
}
