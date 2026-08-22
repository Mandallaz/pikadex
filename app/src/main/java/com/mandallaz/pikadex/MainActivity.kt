package com.mandallaz.pikadex

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.mandallaz.pikadex.data.DisplaySettings
import com.mandallaz.pikadex.navigation.PokedexNavHost
import com.mandallaz.pikadex.ui.LocalizedContext
import com.mandallaz.pikadex.ui.theme.PokeDexTheme

class MainActivity : ComponentActivity() {
    // F119 — declared in the manifest for the F74 prefetch progress notification, but a declared
    // permission is never actually granted on API 33+ without a runtime request: without this,
    // PrefetchManager's foreground service ran invisibly, with no error to explain the missing
    // notification. Registered before onCreate's super call per ActivityResultRegistry's own
    // requirement (must happen before STARTED).
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way: prefetch already runs regardless, see F119 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            val amoledBlack by DisplaySettings.amoledEnabled.collectAsState()
            // Turning AMOLED black on is itself a request for dark mode, not just a modifier of it
            // — the user shouldn't also have to separately flip the system theme.
            val darkTheme = isSystemInDarkTheme() || amoledBlack
            // B27 — provided here, above LocalizedContext, so external-link callers can read the
            // real Activity via LocalActivity.current instead of LocalContext.current, which
            // LocalizedContext overrides with a createConfigurationContext-derived Context that
            // carries no Activity token (see ExternalLinks.kt's own doc for the full story).
            CompositionLocalProvider(LocalActivity provides this) {
                PokeDexTheme(darkTheme = darkTheme, amoledBlack = amoledBlack) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        LocalizedContext {
                            PokedexNavHost()
                        }
                    }
                }
            }
        }
    }

    /** F119 — no-op below API 33 (POST_NOTIFICATIONS didn't exist as a runtime permission yet) and
     *  when already granted, so this is safe to call unconditionally on every launch. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val alreadyGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!alreadyGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
