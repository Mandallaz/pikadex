package com.mandallaz.pikadex.util

import java.net.URI

object UrlValidator {
    /**
     * Checks if a URL is valid based on:
     * - Starting with 'https' scheme
     * - Host is 'raw.githubusercontent.com', 'pokeapi.co', or any subdomain ending with '.pokeapi.co'
     */
    fun isValid(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()

            scheme == "https" && (
                host == "raw.githubusercontent.com" ||
                host == "pokeapi.co" ||
                host?.endsWith(".pokeapi.co") == true
            )
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if a string represents a remote URL as opposed to a local file path.
     */
    fun isRemoteUrl(path: String?): Boolean {
        if (path == null) return false
        return path.startsWith("http://", ignoreCase = true) ||
               path.startsWith("https://", ignoreCase = true) ||
               path.contains("://")
    }
}
