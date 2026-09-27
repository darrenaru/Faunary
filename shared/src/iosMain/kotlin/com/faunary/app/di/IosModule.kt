package com.faunary.app.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.faunary.app.IosBackgroundTasks
import com.faunary.app.data.FaunaryDatabase
import com.faunary.app.data.IosPhotoProcessor
import com.faunary.app.data.PhotoFiles
import com.faunary.app.data.PhotoProcessor
import com.faunary.app.data.documentsDirectory
import com.faunary.app.data.photosDirectory
import com.faunary.app.location.IosLocationSource
import com.faunary.app.location.IosVoiceGuide
import com.faunary.app.location.LocationSource
import com.faunary.app.location.VoiceGuide
import com.faunary.app.ml.AnimalDetector
import com.faunary.app.ml.IosAnimalDetector
import com.faunary.app.notify.IosInteractionNotifier
import com.faunary.app.notify.NotificationInbox
import com.faunary.app.remote.SyncManager
import com.faunary.app.remote.SyncScheduler
import com.faunary.app.ui.detail.CommunityDetailViewModel
import com.faunary.app.ui.detail.DetailViewModel
import com.faunary.app.ui.platform.AppInfo
import com.faunary.app.ui.review.ReviewArgs
import com.faunary.app.ui.review.ReviewViewModel
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

@OptIn(ExperimentalNativeApi::class)
private val iosModule = module {
    single<Settings> { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
    single<Settings>(SearchStore) { NSUserDefaultsSettings(NSUserDefaults(suiteName = "faunary_search")) }
    single<VoiceGuide> { IosVoiceGuide() }
    single {
        Room.databaseBuilder<FaunaryDatabase>(name = "${documentsDirectory()}/faunary.db")
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
    single { IosPhotoProcessor(photosDirectory()) }
    single<PhotoFiles> { get<IosPhotoProcessor>() }
    single<PhotoProcessor> { get<IosPhotoProcessor>() }
    single<AnimalDetector> { IosAnimalDetector(get()) }
    single { IosInteractionNotifier(get(), get()) }
    single<NotificationInbox> { get<IosInteractionNotifier>() }
    single<SyncScheduler> { IosSyncScheduler(get()) }
    single { IosLocationSource() }
    single<LocationSource> { get<IosLocationSource>() }
    single {
        val version = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
        AppInfo(version ?: "?", Platform.isDebugBinary)
    }

    // Opened with koinViewModel { parametersOf(id) } from the iOS nav host.
    viewModel { (id: Long) -> DetailViewModel(id, get(), get()) }
    viewModel { (id: String) -> CommunityDetailViewModel(id, get(), get()) }
    viewModel { (args: ReviewArgs) -> ReviewViewModel(args, get(), get(), get(), get()) }
}

private var koinStarted = false

/** Called once from MainViewController before the first composition. */
fun initKoin() {
    if (koinStarted) return
    koinStarted = true
    startKoin { modules(sharedModule, iosModule) }
}

/**
 * Runs a sync right away; a new request replaces one still running (sync is idempotent). When it
 * can't finish, [IosBackgroundTasks] tries again later, like WorkManager's retry on Android.
 */
private class IosSyncScheduler(private val sync: SyncManager) : SyncScheduler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun schedule() {
        job?.cancel()
        job = scope.launch {
            // Not everything went up (offline?): the background refresh retries later.
            if (!sync.syncAll()) IosBackgroundTasks.schedule()
        }
    }
}
