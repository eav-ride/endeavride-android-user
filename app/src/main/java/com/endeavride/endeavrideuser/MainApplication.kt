package com.endeavride.endeavrideuser

import android.app.Application
import com.google.android.libraries.places.api.Places
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MainApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        Places.initialize(this, "AIzaSyBQLhQPNU5UFQczahI4ZHX4CReuH1D5o8U")
    }
}