package com.faunary.app.remote

import com.faunary.app.data.AnimalSighting
import com.faunary.app.data.SightingDao
import com.faunary.app.data.SyncState
import com.faunary.app.data.localPhotoPath
import com.faunary.app.util.Log
import com.faunary.app.util.randomUuid
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/**
 * Pushes local changes to Supabase. Every sighting is public, so new entries are uploaded,
 * edits are mirrored and deletions are propagated. Local data stays the source of truth.
 */
class SyncManager(
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
        val file = Path(localPhotoPath(s.photoPath))
        if (!SystemFileSystem.exists(file)) return
        // Persist the id first: if the row insert fails after the photo went up, the retry
        // overwrites the same object instead of leaving an orphaned public photo behind.
        val remoteId = s.remoteId ?: randomUuid().also { dao.setRemoteId(s.id, it) }
        val path = photoPath(uid, remoteId)
        client.storage.from(PHOTO_BUCKET).upload(path, SystemFileSystem.source(file).buffered().use { it.readByteArray() }) { upsert = true }
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

/** Queues [SyncManager.syncAll] to run once there is network (WorkManager on Android). */
interface SyncScheduler {
    fun schedule()
}
