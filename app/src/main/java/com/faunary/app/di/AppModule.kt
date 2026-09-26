package com.faunary.app.di

import android.content.Context
import androidx.room.Room
import com.faunary.app.data.FaunaryDatabase
import com.faunary.app.data.SightingDao
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FaunaryDatabase =
        Room.databaseBuilder(context, FaunaryDatabase::class.java, "faunary.db").build()

    @Provides
    fun provideSightingDao(db: FaunaryDatabase): SightingDao = db.sightingDao()

    @Provides
    @Singleton
    fun provideFusedLocation(@ApplicationContext context: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
}
