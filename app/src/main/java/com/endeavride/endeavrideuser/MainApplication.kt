package com.endeavride.endeavrideuser

import android.app.Application
import com.google.android.libraries.places.api.Places
import dagger.hilt.android.HiltAndroidApp

//@HiltAndroidApp
class MainApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        Places.initialize(this, R.string.google_maps_key.toString())
    }
}