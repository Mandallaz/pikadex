package com.mandallaz.pikadex.util

import androidx.lifecycle.ViewModel

/**
 * Cancels [this] ViewModel's `viewModelScope`, same as what `ViewModelStore` does when a real
 * screen goes away. Without this, a ViewModel built directly (as every ViewModel test in this repo
 * does, bypassing the ViewModelStore entirely) leaves its `init{}`-launched collectors — e.g.
 * [com.mandallaz.pikadex.ui.team.TeamViewModel]'s subscription to
 * [com.mandallaz.pikadex.data.TeamRepository.team] — permanently subscribed to their singleton
 * `object`s for the rest of the JVM test run, so a later, unrelated test mutating that singleton
 * would still wake up this long-dead ViewModel's coroutines. `clear$lifecycle_viewmodel` is
 * `internal` in Kotlin (name-mangled, not `private`) specifically so tests in other modules can
 * still reach it via reflection the way this does — there's no other public API to stop a
 * ViewModel outside of an actual `ViewModelStore`.
 */
fun ViewModel.clearForTest() {
    val method = ViewModel::class.java.getMethod("clear\$lifecycle_viewmodel")
    method.invoke(this)
}
