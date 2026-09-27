package com.faunary.app

import android.app.Application
import androidx.work.Configuration
import com.faunary.app.di.appModule
import com.faunary.app.di.sharedModule
import com.faunary.app.notify.InteractionNotifier
import com.faunary.app.remote.LiveLocationSharer
import com.faunary.app.remote.SyncScheduler
import com.faunary.app.update.UpdateScheduler
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.factory.KoinWorkerFactory
import org.koin.core.context.startKoin

class FaunaryApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(KoinWorkerFactory()).build()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@FaunaryApp)
            modules(sharedModule, appModule)
        }
        // Push anything saved while offline, and resume live sharing if the user opted in.
        get<SyncScheduler>().schedule()
        get<LiveLocationSharer>().start()
        get<UpdateScheduler>().start()
        get<InteractionNotifier>().start()
    }
}
