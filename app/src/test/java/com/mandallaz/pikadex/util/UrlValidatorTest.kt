package com.mandallaz.pikadex.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlValidatorTest {

    @Test
    fun `valid allowlisted domains are approved`() {
        // raw.githubusercontent.com
        assertTrue(UrlValidator.isValid("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/25.png"))
        assertTrue(UrlValidator.isValid("https://raw.githubusercontent.com/PokeAPI/cries/main/cries/pokemon/latest/25.ogg"))

        // pokeapi.co
        assertTrue(UrlValidator.isValid("https://pokeapi.co/api/v2/pokemon/25/"))

        // Subdomains of pokeapi.co
        assertTrue(UrlValidator.isValid("https://beta.pokeapi.co/graphql/v1beta"))
        assertTrue(UrlValidator.isValid("https://graphql.pokeapi.co/v1beta2"))
    }

    @Test
    fun `wrong scheme is rejected`() {
        // http instead of https
        assertFalse(UrlValidator.isValid("http://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/25.png"))
        assertFalse(UrlValidator.isValid("http://pokeapi.co/api/v2/pokemon/25/"))
    }

    @Test
    fun `non-allowlisted domains are rejected`() {
        assertFalse(UrlValidator.isValid("https://google.com"))
        assertFalse(UrlValidator.isValid("https://github.com/PokeAPI/sprites"))
        assertFalse(UrlValidator.isValid("https://smogon.com/dex/"))
    }

    @Test
    fun `malicious subdomain trick domains are rejected`() {
        assertFalse(UrlValidator.isValid("https://raw.githubusercontent.com.malicious.com/PokeAPI/sprites/master/sprites/pokemon/25.png"))
        assertFalse(UrlValidator.isValid("https://pokeapi.co.malicious.com/api/v2/pokemon/25/"))
        assertFalse(UrlValidator.isValid("https://evilpokeapi.co"))
        assertFalse(UrlValidator.isValid("https://beta.pokeapi.co.evil.com"))
    }

    @Test
    fun `null or empty or invalid url strings are rejected`() {
        assertFalse(UrlValidator.isValid(null))
        assertFalse(UrlValidator.isValid(""))
        assertFalse(UrlValidator.isValid("   "))
        assertFalse(UrlValidator.isValid("not-a-url"))
    }

    @Test
    fun `isRemoteUrl correctly identifies remote vs local paths`() {
        assertTrue(UrlValidator.isRemoteUrl("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/25.png"))
        assertTrue(UrlValidator.isRemoteUrl("http://example.com"))
        assertTrue(UrlValidator.isRemoteUrl("ftp://files.example.com"))

        // Local files
        assertFalse(UrlValidator.isRemoteUrl("/data/user/0/com.mandallaz.pikadex/cache/cries/25.ogg"))
        assertFalse(UrlValidator.isRemoteUrl("src/main/assets/somefile.txt"))
        assertFalse(UrlValidator.isRemoteUrl(""))
        assertFalse(UrlValidator.isRemoteUrl(null))
    }
}
