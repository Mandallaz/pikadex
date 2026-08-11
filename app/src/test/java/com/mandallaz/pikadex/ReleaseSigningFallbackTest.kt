package com.mandallaz.pikadex

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F68 — the release build type silently falls back to debug signing when `keystore.properties`
 * is absent (see `app/build.gradle.kts`). Guards against that fallback going silent again: a
 * config-time warning and a self-identifying `versionNameSuffix` must both stay wired up.
 */
class ReleaseSigningFallbackTest {

    private val buildScript = File("build.gradle.kts").readText()

    @Test
    fun `debug-signing fallback logs a warning when no keystore is present`() {
        assertTrue(
            "build.gradle.kts should warn at configuration time when keystore.properties is missing",
            buildScript.contains("logger.warn") && buildScript.contains("keystore.properties not found")
        )
    }

    @Test
    fun `debug-signed release build self-identifies via versionNameSuffix`() {
        assertTrue(
            "release build type should suffix versionName when falling back to debug signing",
            buildScript.contains("versionNameSuffix = \"-debugsigned\"")
        )
    }
}
