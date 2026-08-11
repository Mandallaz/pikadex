package com.mandallaz.pikadex

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F64 — every URL in the app is HTTPS, but `usesCleartextTraffic` defaults to true on API 24-27.
 * Guards against a future redirect or new endpoint silently downgrading to HTTP on those versions.
 */
class CleartextTrafficLockdownTest {

    @Test
    fun `manifest disables cleartext traffic`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(
            "AndroidManifest.xml should set android:usesCleartextTraffic=\"false\" on <application>",
            manifest.contains("android:usesCleartextTraffic=\"false\"")
        )
    }
}
