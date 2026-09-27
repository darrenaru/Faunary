package com.faunary.app

import com.faunary.app.notify.NotificationInbox
import com.faunary.app.remote.SyncManager
import com.faunary.app.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow
import kotlin.concurrent.AtomicInt

/**
 * While the app is closed, iOS wakes it now and then (at most every [MIN_INTERVAL_S], when the system
 * sees fit) to push unsynced finds and check for new likes/comments: the WorkManager jobs of Android.
 */
object IosBackgroundTasks : KoinComponent {
    /** Also listed in Info.plist's BGTaskSchedulerPermittedIdentifiers (iosApp/project.yml). */
    const val REFRESH_TASK_ID = "com.faunary.app.refresh"
    private const val MIN_INTERVAL_S = 15 * 60.0

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Must be called before the app finishes launching. */
    fun register() {
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(REFRESH_TASK_ID, usingQueue = null) { task ->
            (task as? BGAppRefreshTask)?.let(::run)
        }
    }

    fun schedule() {
        val request = BGAppRefreshTaskRequest(identifier = REFRESH_TASK_ID).apply {
            earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(MIN_INTERVAL_S)
        }
        if (!BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)) {
            Log.w("FaunaryBackground", "could not schedule the background refresh")
        }
    }

    private fun run(task: BGAppRefreshTask) {
        schedule() // the next wake-up
        val done = AtomicInt(0)
        val finish = { ok: Boolean -> if (done.compareAndSet(0, 1)) task.setTaskCompletedWithSuccess(ok) }
        val job = scope.launch {
            val synced = get<SyncManager>().syncAll()
            val inbox = get<NotificationInbox>()
            val refreshed = !inbox.isAvailable || inbox.refresh()
            finish(synced && refreshed)
        }
        task.expirationHandler = {
            job.cancel()
            finish(false)
        }
    }
}
