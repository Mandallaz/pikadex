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
 * Falls back to a normal intent when no browser supports Custom Tabs, and gives up quietly if the
 * device has no browser at all — an unhandled ActivityNotFoundException here would crash the app
 * over a link.
 */
fun Context.openExternalLink(url: String) {
    val uri = Uri.parse(url)
    try {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(this, uri)
    } catch (e: ActivityNotFoundException) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            // No browser installed; nothing sensible left to do.
        }
    }
}
