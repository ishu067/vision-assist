package com.visionassist.android

object SensitiveAppPolicy {
    private val blockedPackageFragments = setOf(
        "bank",
        "wallet",
        "password",
        "authenticator",
        "keyboard",
        "inputmethod",
    )

    fun shouldSuspend(packageName: String?): Boolean {
        val packageSegments = packageName?.lowercase()?.split('.') ?: return false
        return packageSegments.any { segment ->
            segment == "ime" || blockedPackageFragments.any(segment::contains)
        }
    }
}
