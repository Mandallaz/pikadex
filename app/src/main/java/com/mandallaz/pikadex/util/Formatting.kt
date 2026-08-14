package com.mandallaz.pikadex.util

import kotlin.math.roundToInt

/** Binary units (KiB/MiB, "1024-based"), matching what Android's own storage settings show. */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "${(bytes / (1024.0 * 1024.0) * 10).roundToInt() / 10.0} MB"
    bytes >= 1024 -> "${(bytes / 1024.0).roundToInt()} KB"
    else -> "$bytes B"
}
