package com.faunary.app

import androidx.compose.ui.window.ComposeUIViewController
import com.faunary.app.di.initKoin
import com.faunary.app.notify.IosInteractionNotifier
import com.faunary.app.remote.LiveLocationSharer
import com.faunary.app.remote.SyncScheduler
import com.faunary.app.util.appInForeground
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.mp.KoinPlatform
import platform.UIKit.UIViewController

/**
 * Called from the SwiftUI App's init (iosApp/iosApp/iOSApp.swift), before launching finishes: background
 * tasks and the notification delegate have to be in place by then. The iOS side of FaunaryApp.onCreate.
 */
@Suppress("unused")
fun onAppLaunch() {
    initKoin()
    IosBackgroundTasks.register()
    val koin = KoinPlatform.getKoin()
    koin.get<IosInteractionNotifier>().start()
    // Push anything saved while offline, and resume live sharing if the user opted in.
    koin.get<SyncScheduler>().schedule()
    koin.get<LiveLocationSharer>().start()
    // Leaving the app: ask iOS for a later background refresh.
    appInForeground().filter { !it }.onEach { IosBackgroundTasks.schedule() }.launchIn(MainScope())
}

/** Entry point called from Swift (iosApp/iosApp/ContentView.swift). */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController {
    initKoin()
    return ComposeUIViewController { FaunaryIosApp() }
}
