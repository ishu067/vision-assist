package com.visionassist.android

import org.json.JSONObject

/** User settings only; this is not a diagnosis or a prescription. */
data class VisualProfile(
    val condition: String = "none",
    val spherePower: Float = 0f,
    val cylinderPower: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val sharpness: Float = 0f,
    val colorMode: String = "normal",
    val clahe: Boolean = false,
) {
    fun toJson(): String = JSONObject().apply {
        put("condition", condition)
        put("sphere_power", spherePower)
        put("cylinder_power", cylinderPower)
        put("brightness", brightness)
        put("contrast", contrast)
        put("sharpness", sharpness)
        put("color_mode", colorMode)
        put("clahe", clahe)
    }.toString()

    companion object {
        private val supportedModes = setOf("normal", "protanopia", "deuteranopia", "tritanopia", "grayscale")

        fun fromJson(payload: String): VisualProfile {
            val json = JSONObject(payload)
            val mode = json.optString("color_mode", "normal").lowercase()
            require(mode in supportedModes) { "Unsupported color mode" }
            return VisualProfile(
                condition = json.optString("condition", "none").trim().ifEmpty { "none" },
                spherePower = json.optDouble("sphere_power", 0.0).toFloat().coerceIn(-20f, 20f),
                cylinderPower = json.optDouble("cylinder_power", 0.0).toFloat().coerceIn(-10f, 10f),
                brightness = json.optDouble("brightness", 0.0).toFloat().coerceIn(-100f, 100f),
                contrast = json.optDouble("contrast", 1.0).toFloat().coerceIn(0.5f, 2.5f),
                sharpness = json.optDouble("sharpness", 0.0).toFloat().coerceIn(0f, 2f),
                colorMode = mode,
                clahe = json.optBoolean("clahe", false),
            )
        }
    }
}
