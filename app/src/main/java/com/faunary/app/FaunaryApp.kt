package com.faunary.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.faunary.app.remote.LiveLocationSharer
import com.faunary.app.remote.SyncScheduler
import com.faunary.app.update.UpdateScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FaunaryApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var liveLocationSharer: LiveLocationSharer
    @Inject lateinit var updateScheduler: UpdateScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Push anything saved while offline, and resume live sharing if the user opted in.
        syncScheduler.schedule()
        liveLocationSharer.start()
        updateScheduler.start()
    }
}
