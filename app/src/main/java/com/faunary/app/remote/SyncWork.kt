package com.faunary.app.remote

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val sync: SyncManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (sync.syncAll()) Result.success() else Result.retry()
}

/** Queues a sync that runs as soon as there is network, retrying with backoff. */
class WorkManagerSyncScheduler(
    private val context: Context,
    private val supabase: SupabaseProvider,
) : SyncScheduler {
    override fun schedule() {
        if (!supabase.isConfigured) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // REPLACE (not APPEND): a fresh trigger must not wait behind a job sitting in retry backoff.
        // Sync is idempotent and uploads use upsert, so cancelling a run midway is safe.
        WorkManager.getInstance(context).enqueueUniqueWork("faunary-sync", ExistingWorkPolicy.REPLACE, request)
    }
}
