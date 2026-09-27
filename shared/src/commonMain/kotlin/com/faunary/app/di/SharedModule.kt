package com.faunary.app.di

import com.faunary.app.data.FaunaryDatabase
import com.faunary.app.data.SettingsRepository
import com.faunary.app.data.SightingRepository
import com.faunary.app.location.PlaceSearchRepository
import com.faunary.app.location.RouteRepository
import com.faunary.app.ml.CloudAnimalDetector
import com.faunary.app.remote.CommunityRepository
import com.faunary.app.remote.LiveLocationSharer
import com.faunary.app.remote.LiveRouteRepository
import com.faunary.app.remote.NotificationRepository
import com.faunary.app.remote.PinRepository
import com.faunary.app.remote.SocialRepository
import com.faunary.app.remote.SupabaseProvider
import com.faunary.app.remote.SyncManager
import com.faunary.app.ui.gallery.GalleryViewModel
import com.faunary.app.ui.journal.JournalViewModel
import com.faunary.app.ui.map.MapViewModel
import com.faunary.app.ui.notifications.NotificationsViewModel
import com.faunary.app.ui.profile.ProfileViewModel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Bindings shared by Android and iOS. Each platform module supplies the platform pieces:
 * FaunaryDatabase, the settings store (com.russhwolf.settings.Settings), PhotoFiles, SyncScheduler,
 * LocationSource, PhotoProcessor, AnimalDetector, NotificationInbox, VoiceGuide, AppInfo and
 * the recent-searches store ([SearchStore]).
 */
val sharedModule = module {
    single { get<FaunaryDatabase>().sightingDao() }
    // Engine from the platform: OkHttp on Android, Darwin (URLSession) on iOS.
    single { HttpClient { install(HttpTimeout) } }

    singleOf(::SettingsRepository)
    singleOf(::SightingRepository)
    singleOf(::SupabaseProvider)
    singleOf(::CommunityRepository)
    singleOf(::LiveRouteRepository)
    singleOf(::LiveLocationSharer)
    singleOf(::NotificationRepository)
    singleOf(::PinRepository)
    singleOf(::SocialRepository)
    singleOf(::SyncManager)
    singleOf(::CloudAnimalDetector)
    singleOf(::RouteRepository)
    single { PlaceSearchRepository(get(), get(SearchStore)) }

    viewModelOf(::GalleryViewModel)
    viewModelOf(::JournalViewModel)
    viewModelOf(::MapViewModel)
    viewModelOf(::NotificationsViewModel)
    viewModelOf(::ProfileViewModel)
}

/** Qualifier of the separate settings store that keeps the map's recent searches. */
val SearchStore = named("search")
