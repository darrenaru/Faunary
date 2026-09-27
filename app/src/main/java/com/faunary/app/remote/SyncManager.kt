package com.faunary.app.remote

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.faunary.app.data.AnimalSighting
import com.faunary.app.data.SightingDao
import com.faunary.app.data.SyncState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes local changes to Supabase. Every sighting is public, so new entries are uploaded,
 * edits are mirrored and deletions are propagated. Local data stays the source of truth.
 */
@Singleton
class SyncManager @Inject constructor(
    private val supabase: SupabaseProvider,
    private val dao: SightingDao,
) {
    /** @return true when everything is in sync (or online features are off). */
    suspend fun syncAll(): Boolean = withContext(Dispatchers.IO) {
        val client = supabase.client ?: return@withContext true
        val uid = supabase.ensureUserId() ?: return@withContext false
        var ok = true

        for (pending in dao.pendingDeletes()) {
            runCatching {
                client.from("sightings").delete { filter { eq("id", pending.remoteId) } }
                pending.photoPath?.let { client.storage.from(PHOTO_BUCKET).delete(it) }
                dao.clearPendingDelete(pending.remoteId)
            }.onFailure { ok = false; Log.w(TAG, "delete failed", it) }
        }

        for (s in dao.unsynced()) {
            runCatching {
                when (s.syncState) {
                    SyncState.PENDING -> upload(uid, s)
                    SyncState.DIRTY -> pushEdit(uid, s)
                }
            }.onFailure { ok = false; Log.w(TAG, "sync ${s.id} failed", it) }
        }
        ok
    }

    private suspend fun upload(uid: String, s: AnimalSighting) {
        val client = supabase.client ?: return
        val file = File(s.photoPath)
        if (!file.exists()) return
        // Persist the id first: if the row insert fails after the photo went up, the retry
        // overwrites the same object instead of leaving an orphaned public photo behind.
        val remoteId = s.remoteId ?: UUID.randomUUID().toString().also { dao.setRemoteId(s.id, it) }
        val path = photoPath(uid, remoteId)
        client.storage.from(PHOTO_BUCKET).upload(path, file.readBytes()) { upsert = true }
        client.from("sightings").upsert(s.toDto(remoteId, path))
        dao.markSynced(s.id, remoteId)
    }

    private suspend fun pushEdit(uid: String, s: AnimalSighting) {
        val client = supabase.client ?: return
        val remoteId = s.remoteId ?: return upload(uid, s)
        client.from("sightings").upsert(s.toDto(remoteId, photoPath(uid, remoteId)))
        dao.markSynced(s.id, remoteId)
    }

    private fun AnimalSighting.toDto(remoteId: String, path: String) = SightingDto(
        id = remoteId,
        animalLabel = animalLabel,
        category = category,
        confidence = confidence,
        aiLabel = aiLabel,
        latitude = latitude,
        longitude = longitude,
        locationName = locationName,
        note = note,
        photoPath = path,
        photoWidth = photoWidth,
        photoHeight = photoHeight,
        detections = detections,
        takenAtMs = timestamp,
    )

    companion object {
        private const val TAG = "FaunarySync"
        fun photoPath(uid: String, remoteId: String) = "$uid/$remoteId.jpg"
    }
}

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: SyncManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (sync.syncAll()) Result.success() else Result.retry()
}

/** Queues a sync that runs as soon as there is network, retrying with backoff. */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseProvider,
) {
    fun schedule() {
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
