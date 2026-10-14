package com.visionassist.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveAppPolicyTest {
    @Test
    fun suspendsPackagesMatchingSensitiveNamePatterns() {
        assertTrue(SensitiveAppPolicy.shouldSuspend("com.example.bankapp"))
        assertTrue(SensitiveAppPolicy.shouldSuspend("com.example.wallet"))
        assertTrue(SensitiveAppPolicy.shouldSuspend("com.example.passwordmanager"))
        assertTrue(SensitiveAppPolicy.shouldSuspend("com.example.authenticator"))
        assertTrue(SensitiveAppPolicy.shouldSuspend("com.example.keyboard"))
        assertTrue(SensitiveAppPolicy.shouldSuspend("com.example.ime.input"))
        assertTrue(SensitiveAppPolicy.shouldSuspend("com.google.android.inputmethod.latin"))
    }

    @Test
    fun doesNotSuspendUnmatchedPackages() {
        assertFalse(SensitiveAppPolicy.shouldSuspend("com.example.reader"))
        assertFalse(SensitiveAppPolicy.shouldSuspend("com.example.runtime"))
        assertFalse(SensitiveAppPolicy.shouldSuspend(null))
    }
}
