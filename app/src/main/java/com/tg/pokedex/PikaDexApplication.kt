package com.tg.pokedex

import android.app.Application
import com.tg.pokedex.data.FavoritesRepository

class PikaDexApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FavoritesRepository.init(this)
    }
}
