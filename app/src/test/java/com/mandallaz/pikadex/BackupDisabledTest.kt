package com.mandallaz.pikadex

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B28 — PRIVACY_POLICY.md states favorites/teams/settings "stay on your device only" and are
 * "deleted if you uninstall the app". `android:allowBackup="true"` (the default, and what this
 * manifest used to set) would let Android Auto Backup upload that SharedPreferences data to the
 * user's Google Drive and let it survive an uninstall, contradicting the policy.
 */
class BackupDisabledTest {

    @Test
    fun `the manifest disables Android auto backup`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(
            "AndroidManifest.xml should set android:allowBackup=\"false\" on <application>",
            manifest.contains("android:allowBackup=\"false\"")
        )
    }
}
