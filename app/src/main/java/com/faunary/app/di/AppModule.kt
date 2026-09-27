package com.faunary.app.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import androidx.room.Room
import com.faunary.app.BuildConfig
import com.faunary.app.data.FaunaryDatabase
import com.faunary.app.data.PhotoFiles
import com.faunary.app.data.PhotoProcessor
import com.faunary.app.data.PhotoStorage
import com.faunary.app.di.SearchStore
import com.faunary.app.location.AndroidVoiceGuide
import com.faunary.app.location.LocationRepository
import com.faunary.app.location.LocationSource
import com.faunary.app.location.VoiceGuide
import com.faunary.app.ml.AndroidAnimalDetector
import com.faunary.app.ml.AnimalDetector
import com.faunary.app.ml.OnDeviceAnimalDetector
import com.faunary.app.notify.InteractionNotifier
import com.faunary.app.notify.NotificationInbox
import com.faunary.app.notify.NotificationWorker
import com.faunary.app.remote.SyncScheduler
import com.faunary.app.remote.SyncWorker
import com.faunary.app.remote.WorkManagerSyncScheduler
import com.faunary.app.ui.camera.CameraViewModel
import com.faunary.app.ui.detail.CommunityDetailViewModel
import com.faunary.app.ui.detail.DetailViewModel
import com.faunary.app.ui.navigation.CommunityDetailRoute
import com.faunary.app.ui.navigation.DetailRoute
import com.faunary.app.ui.navigation.ReviewRoute
import com.faunary.app.ui.platform.AppInfo
import com.faunary.app.ui.review.ReviewArgs
import com.faunary.app.ui.review.ReviewViewModel
import com.faunary.app.update.UpdateInstaller
import com.faunary.app.update.UpdateRepository
import com.faunary.app.update.UpdateScheduler
import com.faunary.app.update.UpdateViewModel
import com.faunary.app.update.UpdateWorker
import com.google.android.gms.location.LocationServices
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Android-only bindings; [sharedModule] holds the ones shared with iOS. */
val appModule = module {
    single { Room.databaseBuilder(androidContext(), FaunaryDatabase::class.java, "faunary.db").build() }
    single { LocationServices.getFusedLocationProviderClient(androidContext()) }
    single { AppInfo(BuildConfig.VERSION_NAME, BuildConfig.DEBUG) }

    singleOf(::PhotoStorage) {
        bind<PhotoFiles>()
        bind<PhotoProcessor>()
    }
    // Same preferences file as before the move to the shared module, so existing settings carry over.
    single<Settings> { SharedPreferencesSettings(androidContext().getSharedPreferences("faunary_settings", Context.MODE_PRIVATE)) }
    single<Settings>(SearchStore) { SharedPreferencesSettings(androidContext().getSharedPreferences("faunary_search", Context.MODE_PRIVATE)) }
    singleOf(::LocationRepository) { bind<LocationSource>() }
    singleOf(::AndroidVoiceGuide) { bind<VoiceGuide>() }
    singleOf(::AndroidAnimalDetector) { bind<AnimalDetector>() }
    singleOf(::OnDeviceAnimalDetector)
    singleOf(::InteractionNotifier) { bind<NotificationInbox>() }
    singleOf(::WorkManagerSyncScheduler) { bind<SyncScheduler>() }
    singleOf(::UpdateRepository)
    singleOf(::UpdateScheduler)
    singleOf(::UpdateInstaller)

    workerOf(::SyncWorker)
    workerOf(::NotificationWorker)
    workerOf(::UpdateWorker)

    viewModelOf(::CameraViewModel)
    // The shared view models take their id directly; here it comes from the nav route's arguments.
    viewModel { CommunityDetailViewModel(get<SavedStateHandle>().toRoute<CommunityDetailRoute>().id, get(), get()) }
    viewModel { DetailViewModel(get<SavedStateHandle>().toRoute<DetailRoute>().id, get(), get()) }
    viewModel {
        val route = get<SavedStateHandle>().toRoute<ReviewRoute>()
        ReviewViewModel(ReviewArgs(route.photoPath, route.capturedAt, route.captureFix), get(), get(), get(), get())
    }
    viewModelOf(::UpdateViewModel)
}
