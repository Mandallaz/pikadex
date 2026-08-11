package com.mandallaz.pikadex

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * F67 — lifecycle-runtime-ktx and lifecycle-viewmodel-compose ship from the same androidx.lifecycle
 * release train and should move together; letting them drift lets Gradle silently resolve one of
 * them to a different minor version than the pin implies.
 */
class DependencyVersionAlignmentTest {

    private fun versionOf(key: String): String {
        val catalog = File("../gradle/libs.versions.toml").readText()
        val match = Regex("""$key\s*=\s*"([^"]+)"""").find(catalog)
        return requireNotNull(match) { "no version entry named '$key' in libs.versions.toml" }.groupValues[1]
    }

    @Test
    fun `lifecycle-runtime-ktx and lifecycle-viewmodel-compose are pinned to the same version`() {
        assertEquals(versionOf("lifecycleRuntimeKtx"), versionOf("lifecycleViewmodelCompose"))
    }
}
