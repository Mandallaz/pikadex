package com.mandallaz.pikadex.ui.settings

import com.mandallaz.pikadex.R
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SMOGON_TIER_EXPLANATION] is the content behind the "?" help icon in Settings (issue #30). Since
 * B8, its text lives in `strings.xml`/`values-fr/strings.xml` (so it follows F35's language
 * picker) rather than as inline Kotlin literals — so unlike before, there's no raw text on
 * [TierExplanationSection] to assert against directly in a plain JVM test. Instead this reads
 * `values/strings.xml` off disk (a plain XML parse, no Android/Compose runtime needed) and checks
 * the `smogon_tier_*` entries there, which is what a Compose `stringResource()` call would
 * ultimately resolve to at runtime for the `en` (default) locale.
 *
 * This is also the regression test for B8 itself: before that fix, every `smogon_tier_*` key
 * below was simply absent from strings.xml (the text lived as Kotlin string literals instead) —
 * this test fails on that state and passes once the keys exist.
 */
class SmogonTierExplanationDialogTest {

    private fun stringsXml(resourceDir: String): Map<String, String> {
        val file = File("src/main/res/$resourceDir/strings.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val node = nodes.item(i)
            val name = node.attributes.getNamedItem("name").nodeValue
            name to node.textContent
        }
    }

    private val englishStrings = stringsXml("values")
    private val frenchStrings = stringsXml("values-fr")

    private val smogonKeys = listOf(
        "smogon_tier_dialog_title", "smogon_tier_dialog_close",
        "smogon_tier_intro_1", "smogon_tier_intro_2",
        "smogon_tier_core_concept_heading", "smogon_tier_core_concept_body",
        "smogon_tier_format_scope_heading", "smogon_tier_format_scope_body",
        "smogon_tier_primary_tiers_heading", "smogon_tier_primary_tiers_body",
        "smogon_tier_usage_threshold_heading", "smogon_tier_usage_threshold_body",
        "smogon_tier_mobility_heading", "smogon_tier_mobility_body",
        "smogon_tier_banlists_heading", "smogon_tier_banlists_body",
        "smogon_tier_how_tiers_change_heading", "smogon_tier_how_tiers_change_body",
        "smogon_tier_bl_status_heading", "smogon_tier_bl_status_body",
        "smogon_tier_community_voting_heading", "smogon_tier_community_voting_body"
    )

    // Reflection maps each declared R.string.smogon_tier_* field's int id back to its resource
    // name — the R class' int constants are generated at compile time and available to a plain
    // JVM unit test with no Robolectric needed; only reading the *string values* they point to
    // requires the strings.xml parse above.
    private val idToName: Map<Int, String> = smogonKeys.associateBy { key -> R.string::class.java.getField(key).getInt(null) }

    @Test
    fun `every SMOGON_TIER_EXPLANATION section's resource ids resolve to a non-blank English string`() {
        assertTrue("expected at least the 11 documented sections", SMOGON_TIER_EXPLANATION.size >= 11)
        SMOGON_TIER_EXPLANATION.forEach { section ->
            listOfNotNull(section.headingRes, section.bodyRes).forEach { resId ->
                val name = idToName[resId] ?: error("resource id $resId not one of the known smogon_tier_* keys")
                assertTrue("expected $name to be non-blank", englishStrings[name]?.isNotBlank() == true)
            }
        }
    }

    @Test
    fun `covers every primary tier code`() {
        val body = englishStrings.getValue("smogon_tier_primary_tiers_body")
        listOf("AG", "Uber", "OU", "UU", "RU", "NU", "PU", "ZU").forEach { tierCode ->
            assertTrue("expected $tierCode to be mentioned", body.contains(tierCode))
        }
    }

    // B8's actual regression: the dialog rendered in English regardless of the picked language
    // because these keys didn't exist in values-fr at all — every English key must have a French
    // counterpart, not just the ones some other screen happens to already cover.
    @Test
    fun `every Smogon tier key has a French translation`() {
        smogonKeys.forEach { key ->
            assertTrue("expected values/strings.xml to define $key", englishStrings.containsKey(key))
            assertTrue("expected values-fr/strings.xml to define $key", frenchStrings.containsKey(key))
            assertTrue("expected $key's French translation to be non-blank", frenchStrings[key]?.isNotBlank() == true)
        }
    }
}
