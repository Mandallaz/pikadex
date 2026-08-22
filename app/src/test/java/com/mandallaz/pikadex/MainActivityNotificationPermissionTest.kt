package com.mandallaz.pikadex

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * F119 — POST_NOTIFICATIONS is declared in the manifest for the F74 prefetch progress
 * notification, but a declared permission is never actually granted on API 33+ without an
 * explicit runtime request. Without it the notification was silently suppressed with nothing to
 * explain why. Verifies MainActivity actually asks for it, and only when it needs to.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityNotificationPermissionTest {

    @Config(sdk = [33])
    @Test
    fun `requests POST_NOTIFICATIONS on API 33+ when not already granted`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().get()

        val request = shadowOf(activity).lastRequestedPermission
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), request?.requestedPermissions?.toList())
    }

    @Config(sdk = [33])
    @Test
    fun `does not re-request POST_NOTIFICATIONS on API 33+ when already granted`() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        shadowOf(app as android.app.Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().get()

        assertNull(shadowOf(activity).lastRequestedPermission)
    }

    @Config(sdk = [32])
    @Test
    fun `does not request POST_NOTIFICATIONS below API 33`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().get()

        assertNull(shadowOf(activity).lastRequestedPermission)
    }
}
