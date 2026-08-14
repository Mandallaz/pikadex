package com.mandallaz.pikadex.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F112 — scaffolding only (issue #174): proves the module builds and links against the real
 * [BaselineProfileRule]/UiAutomator APIs, but doesn't yet drive a real user journey (launch, scroll
 * the list, open a detail screen). That's #175 (F113) — kept out of this ticket on purpose, per the
 * split rationale (two prior Jules sessions stalled trying to land both the wiring and the real
 * generator content in one pass).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.mandallaz.pikadex") {
        pressHome()
        startActivityAndWait()
        // #175 fills this in: scroll PokedexListScreen, open a detail screen, etc.
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 5_000)
    }
}
