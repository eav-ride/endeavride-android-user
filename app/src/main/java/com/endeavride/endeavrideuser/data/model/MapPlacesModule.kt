package com.endeavride.endeavrideuser.data.model

import android.content.Context
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MapPlacesModule {
    @Singleton
    @Provides
    fun providePlacesClient(@ApplicationContext context: Context): PlacesClient =
        Places.createClient(context)
}