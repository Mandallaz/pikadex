package com.mandallaz.pikadex.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * B13 — a user-facing message a ViewModel can build without embedding actual language text, so it
 * resolves through [LocalizedContext] (the app's picked language) instead of always being English.
 * [args] are passed straight to `stringResource`'s format-arg overload, so `%1$s`/%1$d placeholders
 * in the referenced resource work exactly as they do everywhere else in this app.
 */
data class UiText(@param:StringRes val resId: Int, val args: List<Any> = emptyList()) {
    @Composable
    fun resolve(): String = stringResource(resId, *args.toTypedArray())
}
