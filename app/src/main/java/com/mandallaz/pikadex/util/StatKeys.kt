package com.mandallaz.pikadex.util

import com.mandallaz.pikadex.data.remote.dto.PokemonDto

val BASE_STATS = listOf("hp", "attack", "defense", "special-attack", "special-defense", "speed")

const val TOTAL = "total"

fun PokemonDto.baseStatTotal(): Int = stats.orEmpty().sumOf { it.baseStat }

fun Map<String, Int>.statTotal(): Int = BASE_STATS.sumOf { this[it] ?: 0 }
