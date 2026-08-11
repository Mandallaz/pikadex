package com.mandallaz.pikadex.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Opens [url] in a Custom Tab — an in-app browser that keeps PikaDex's own task, so closing it
 * returns straight here.
 *
 * A plain `ACTION_VIEW` intent hands the link to the browser as a *separate task*: the page opens
 * fine, but getting back means going through the app switcher and finding PikaDex's card behind the
 * browser's, which reads as "I can't get back to the app". Custom Tabs is the standard fix, and
 * still uses the user's real browser (cookies, logins, password manager) rather than an embedded
 * WebView.
 *
 * B22 — the receiver here is routinely [com.mandallaz.pikadex.ui.LocalizedContext]'s
 * `createConfigurationContext`-derived `Context` (F35/B8's app-wide locale override read through
 * `LocalContext.current`), which carries no Activity token even though it wraps one. Without
 * `FLAG_ACTIVITY_NEW_TASK`, `startActivity`-style calls made from it — including
 * `CustomTabsIntent.launchUrl`'s own internal `startActivity` — throw
 * `AndroidRuntimeException: Calling startActivity() from outside of an Activity context requires
 * the FLAG_ACTIVITY_NEW_TASK flag`, which crashed the app on every Smogon link tap. Adding the flag
 * to both the Custom Tabs intent and the `ACTION_VIEW` fallback makes both launches work from any
 * `Context`, Activity-backed or not.
 *
 * Falls back to a normal intent when no browser supports Custom Tabs, and gives up quietly if the
 * device has no browser at all — an unhandled ActivityNotFoundException here would crash the app
 * over a link.
 */
fun Context.openExternalLink(url: String) {
    val uri = Uri.parse(url)
    try {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        customTabsIntent.launchUrl(this, uri)
    } catch (e: ActivityNotFoundException) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            // No browser installed; nothing sensible left to do.
        }
    }
}
