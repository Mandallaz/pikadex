package com.mandallaz.pikadex

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F35 — every `values-{locale}/strings.xml` this app ships must define the exact same key set as
 * the default `values/strings.xml` (minus `app_name`, deliberately `translatable="false"` — a
 * brand name stays as-is across locales). Catches the class of bug B8/B9 both were: a screen's
 * strings added to the default resources but never carried through to every locale, silently
 * falling back to English for just that screen rather than failing a build. Android's own lint
 * (`MissingTranslation`) already catches missing *keys*; this additionally guards against a
 * present-but-blank translation, which lint doesn't flag.
 */
class StringsLocalizationTest {

    private fun stringKeys(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val node = nodes.item(i)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    private val resDir = File("src/main/res")
    private val defaultKeys = stringKeys(File(resDir, "values/strings.xml")) - "app_name"
    private val localeDirs = resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }.orEmpty()

    @Test
    fun `at least the 10 F35 picker languages ship a strings xml`() {
        val expectedDirs = setOf(
            "values-fr", "values-de", "values-es", "values-b+es+419", "values-it",
            "values-pt-rBR", "values-ja", "values-ko", "values-b+zh+Hans", "values-b+zh+Hant"
        )
        val actualDirs = localeDirs.map { it.name }.toSet()
        expectedDirs.forEach { expected ->
            assertTrue("expected $expected to exist under values*/strings.xml", expected in actualDirs)
        }
    }

    @Test
    fun `every locale defines the exact same key set as the default English resources`() {
        localeDirs.forEach { dir ->
            val file = File(dir, "strings.xml")
            if (!file.exists()) return@forEach
            val keys = stringKeys(file).keys
            val missing = defaultKeys.keys - keys
            val extra = keys - defaultKeys.keys
            assertTrue("${dir.name} is missing keys: $missing", missing.isEmpty())
            assertTrue("${dir.name} has unexpected extra keys: $extra", extra.isEmpty())
        }
    }

    @Test
    fun `no locale has a blank translation for a key the default resources define non-blank`() {
        localeDirs.forEach { dir ->
            val file = File(dir, "strings.xml")
            if (!file.exists()) return@forEach
            stringKeys(file).forEach { (key, value) ->
                assertTrue("${dir.name}'s $key is blank", value.isNotBlank())
            }
        }
    }
}
