package com.visionassist.android

object SensitiveAppPolicy {
    private val blockedPackageFragments = setOf(
        "bank",
        "wallet",
        "password",
        "authenticator",
        "keyboard",
        "ime",
    )

    fun shouldSuspend(packageName: String?): Boolean {
        val normalized = packageName?.lowercase() ?: return false
        return blockedPackageFragments.any(normalized::contains)
    }
}
