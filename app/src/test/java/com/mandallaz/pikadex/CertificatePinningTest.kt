package com.mandallaz.pikadex

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F109 — Verify certificate pinning and network security config on Mandallaz/pikadex.
 * Ensures that both CertificatePinner in OkHttp and network_security_config.xml are set up correctly.
 */
class CertificatePinningTest {

    @Test
    fun `manifest references network security config`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(
            "AndroidManifest.xml should set android:networkSecurityConfig=\"@xml/network_security_config\" on <application>",
            manifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\"")
        )
    }

    @Test
    fun `network security config disables cleartext globally and contains domain pins`() {
        val configFile = File("src/main/res/xml/network_security_config.xml")
        assertTrue("network_security_config.xml must exist", configFile.exists())
        val configText = configFile.readText()

        assertTrue(
            "network_security_config.xml should disable cleartext traffic globally",
            configText.contains("cleartextTrafficPermitted=\"false\"")
        )

        // Ensure domains are pinned
        assertTrue(configText.contains("<domain includeSubdomains=\"true\">pokeapi.co</domain>"))
        assertTrue(configText.contains("<domain includeSubdomains=\"true\">githubusercontent.com</domain>"))

        // Ensure expected pins are present
        val expectedPins = listOf(
            "vI2c4MzHEbIyjzPN4chWo00EfZeCrlu7OrQuswZxK5Q=",
            "kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=",
            "mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=",
            "p4MbBTfU3MTzqPxM2Cjv0q3WON8L6FqSzam65NVEkcM=",
            "LoMHBotttiDko50Gi13uXW71eIy7LAttI+rYT8wXF4w=",
            "fk6IOKit1ild5647BH06ujSIq5XbCgqlbYl6ANhhi88="
        )

        for (pin in expectedPins) {
            assertTrue(
                "network_security_config.xml should contain pin digest: $pin",
                configText.contains(pin)
            )
        }
    }

    @Test
    fun `AppContainer configures CertificatePinner with expected domains and pins`() {
        val appContainerFile = File("src/main/java/com/mandallaz/pikadex/data/AppContainer.kt")
        assertTrue("AppContainer.kt must exist", appContainerFile.exists())
        val code = appContainerFile.readText()

        assertTrue("AppContainer should use CertificatePinner", code.contains("CertificatePinner"))

        val expectedDomains = listOf(
            "pokeapi.co",
            "graphql.pokeapi.co",
            "raw.githubusercontent.com"
        )

        for (domain in expectedDomains) {
            assertTrue(
                "AppContainer should define certificate pinning for domain: $domain",
                code.contains("\"$domain\"")
            )
        }

        val expectedPins = listOf(
            "vI2c4MzHEbIyjzPN4chWo00EfZeCrlu7OrQuswZxK5Q=",
            "kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=",
            "mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=",
            "p4MbBTfU3MTzqPxM2Cjv0q3WON8L6FqSzam65NVEkcM=",
            "LoMHBotttiDko50Gi13uXW71eIy7LAttI+rYT8wXF4w=",
            "fk6IOKit1ild5647BH06ujSIq5XbCgqlbYl6ANhhi88="
        )

        for (pin in expectedPins) {
            assertTrue(
                "AppContainer should configure pin: $pin",
                code.contains(pin)
            )
        }
    }
}
