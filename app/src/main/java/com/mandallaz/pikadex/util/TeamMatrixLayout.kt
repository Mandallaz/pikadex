package com.mandallaz.pikadex.util

import androidx.compose.ui.unit.Dp

/**
 * Whether [TeamScreen][com.mandallaz.pikadex.ui.team.TeamScreen]'s coverage matrix should fall
 * back to letting the whole screen scroll as one page, rather than pinning the sprite/type-name
 * header and giving the matrix the leftover space in its own scrollable viewport.
 *
 * Pulled out as a pure function (rather than inlined at the call site) so the decision — "is there
 * enough room left after the real, measured header for the pinned layout to be worth it" — has a
 * regression test. It used to compare the *total* viewport height against a hardcoded guess of the
 * header's size instead of the header's actual measured height; once the suggestions card grew
 * (tier-ceiling line, wider tiles, multi-line "why" text), that guess went stale and the matrix
 * became unreachable on an ordinary portrait phone with no scroll gesture able to recover it.
 */
fun isCompactMatrixLayout(maxHeight: Dp, headerHeight: Dp, minRemainingHeight: Dp): Boolean =
    (maxHeight - headerHeight) < minRemainingHeight
