package com.mandallaz.pikadex.util

import android.app.Activity
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
 * B22/B27 — [com.mandallaz.pikadex.ui.LocalizedContext]'s `createConfigurationContext`-derived
 * `Context` (F35/B8's app-wide locale override read through `LocalContext.current`) carries no
 * Activity token even though it wraps one. `CustomTabsIntent.launchUrl`'s internal `startActivity`
 * throws `AndroidRuntimeException: Calling startActivity() from outside of an Activity context
 * requires the FLAG_ACTIVITY_NEW_TASK flag` when called from a `Context` like that (B22's crash).
 *
 * B22 fixed the crash by always adding `FLAG_ACTIVITY_NEW_TASK`, but that flag is exactly what
 * this doc comment says Custom Tabs was chosen to avoid: it makes the tab a *separate task* again,
 * so closing it can land on the launcher instead of back on PikaDex. B27 fixes that regression:
 * callers now pass the real `Activity` (via `LocalActivity.current`, provided above
 * `LocalizedContext` in `MainActivity` — see that file) when they have one, so the common case
 * launches without the flag and keeps its own-task behavior. The flag is only added when the
 * receiver genuinely isn't an `Activity` (defensive fallback — no known caller hits this today).
 *
 * Falls back to a normal intent when no browser supports Custom Tabs, and gives up quietly if the
 * device has no browser at all — an unhandled ActivityNotFoundException here would crash the app
 * over a link.
 */
fun Context.openExternalLink(url: String) {
    val uri = Uri.parse(url)
    val needsNewTaskFlag = this !is Activity
    try {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        if (needsNewTaskFlag) {
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        customTabsIntent.launchUrl(this, uri)
    } catch (e: ActivityNotFoundException) {
        try {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, uri)
            if (needsNewTaskFlag) {
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(fallbackIntent)
        } catch (e: ActivityNotFoundException) {
            // No browser installed; nothing sensible left to do.
        }
    }
}
