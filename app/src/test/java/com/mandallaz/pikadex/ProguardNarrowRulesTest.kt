package com.mandallaz.pikadex

import com.google.gson.Gson
import com.squareup.moshi.Moshi
import com.mandallaz.pikadex.data.remote.PokeApiGraphQLDataSource
import com.mandallaz.pikadex.data.repository.PokemonDetailBundle
import com.mandallaz.pikadex.data.remote.dto.PokemonDto
import com.mandallaz.pikadex.data.remote.dto.PokemonSpeciesDto
import com.mandallaz.pikadex.data.remote.dto.NamedApiResource
import com.mandallaz.pikadex.data.remote.dto.PokemonSprites
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProguardNarrowRulesTest {

    @Test
    fun `proguard rules are narrowed down correctly`() {
        val rulesFile = File("proguard-rules.pro")
        val actualFile = if (rulesFile.exists()) {
            rulesFile
        } else {
            File("app/proguard-rules.pro")
        }
        assertTrue("proguard-rules.pro file should exist", actualFile.exists())

        val rulesText = actualFile.readText()

        // Assert that the old broad rule is not present
        assertFalse(
            "proguard-rules.pro should not keep the entire data/remote package",
            rulesText.contains("-keep class com.mandallaz.pikadex.data.remote.** { <fields>; }")
        )

        // Assert that the new narrow rules are present
        assertTrue(
            "proguard-rules.pro should keep dto package",
            rulesText.contains("-keep class com.mandallaz.pikadex.**.dto.** { <fields>; }")
        )

        assertTrue(
            "proguard-rules.pro should keep GraphQL nested classes",
            rulesText.contains("-keep class com.mandallaz.pikadex.data.remote.PokeApiGraphQLDataSource\$* { <fields>; }")
        )

        assertTrue(
            "proguard-rules.pro should keep PokemonDetailBundle class",
            rulesText.contains("-keep class com.mandallaz.pikadex.data.repository.PokemonDetailBundle { <fields>; }")
        )
    }

    @Test
    fun `proguard rules keep Room-generated database classes for WorkManager`() {
        val rulesFile = File("proguard-rules.pro")
        val actualFile = if (rulesFile.exists()) {
            rulesFile
        } else {
            File("app/proguard-rules.pro")
        }
        val rulesText = actualFile.readText()

        // B63 — WorkManager's internal WorkDatabase (a Room database) is instantiated
        // reflectively by its generated *_Impl class and no-arg constructor. Without these
        // rules R8 strips that constructor, crashing the app on launch before MainActivity
        // ever opens.
        assertTrue(
            "proguard-rules.pro should keep RoomDatabase subclasses",
            rulesText.contains("-keep class * extends androidx.room.RoomDatabase")
        )
        assertTrue(
            "proguard-rules.pro should keep WorkDatabase_Impl's constructor",
            rulesText.contains("-keep class androidx.work.impl.WorkDatabase_Impl { <init>(...); }")
        )
    }

    @Test
    fun `DTOs can be correctly serialized and deserialized via Gson`() {
        val gson = Gson()

        // Test PokemonDetailBundle serialization/deserialization
        val pokemon = PokemonDto(
            id = 25,
            name = "pikachu",
            height = 4,
            weight = 60,
            baseExperience = 112,
            types = emptyList(),
            stats = emptyList(),
            abilities = emptyList(),
            moves = emptyList(),
            sprites = PokemonSprites(null, null, null),
            species = NamedApiResource("pikachu", "https://pokeapi.co/api/v2/pokemon-species/25/")
        )
        val species = PokemonSpeciesDto(
            id = 25,
            name = "pikachu",
            evolutionChain = null,
            flavorTextEntries = emptyList(),
            genera = emptyList(),
            color = NamedApiResource("yellow", ""),
            eggGroups = emptyList(),
            generation = NamedApiResource("generation-i", ""),
            isLegendary = false,
            isMythical = false,
            varieties = emptyList()
        )
        val bundle = PokemonDetailBundle(
            pokemon = pokemon,
            species = species,
            evolutionChain = null
        )

        val bundleJson = gson.toJson(bundle)
        val deserializedBundle = gson.fromJson(bundleJson, PokemonDetailBundle::class.java)

        assertNotNull(deserializedBundle)
        assertEquals(25, deserializedBundle.pokemon.id)
        assertEquals("pikachu", deserializedBundle.pokemon.name)
        assertEquals(25, deserializedBundle.species.id)
        assertEquals("pikachu", deserializedBundle.species.name)
    }

    @Test
    fun `GraphQL DTOs can be correctly serialized and deserialized via Moshi`() {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(PokeApiGraphQLDataSource.PokemonBasics::class.java)

        val basics = PokeApiGraphQLDataSource.PokemonBasics(
            stats = mapOf("hp" to 45, "attack" to 49),
            types = listOf("grass", "poison"),
            isLegendary = false,
            isMythical = false
        )

        val json = adapter.toJson(basics)
        val deserialized = adapter.fromJson(json)

        assertNotNull(deserialized)
        assertEquals(basics, deserialized)
    }
}
