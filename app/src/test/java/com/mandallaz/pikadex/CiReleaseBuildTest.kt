package com.mandallaz.pikadex

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F62 — CI used to only ever compile/test the debug variant, which has minification off, so it
 * never exercised the ProGuard/Gson risk documented in proguard-rules.pro. Guards against that
 * coverage gap reopening.
 */
class CiReleaseBuildTest {

    @Test
    fun `CI workflow builds the release variant`() {
        val workflow = File("../.github/workflows/ci.yml").readText()
        assertTrue(
            "ci.yml should run ./gradlew assembleRelease so R8/ProGuard is exercised in CI",
            workflow.contains("assembleRelease")
        )
    }
}
