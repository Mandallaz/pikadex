package com.mandallaz.pikadex

import android.app.Application
import com.mandallaz.pikadex.data.FavoritesRepository

class PikaDexApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FavoritesRepository.init(this)
    }
}
