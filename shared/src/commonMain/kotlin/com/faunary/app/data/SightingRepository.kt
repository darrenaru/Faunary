package com.faunary.app.data

import com.faunary.app.remote.SupabaseProvider
import com.faunary.app.remote.SyncManager
import com.faunary.app.remote.SyncScheduler
import kotlinx.coroutines.flow.Flow

class SightingRepository(
    private val dao: SightingDao,
    private val photos: PhotoFiles,
    private val sync: SyncScheduler,
    private val supabase: SupabaseProvider,
) {
    fun observeAll(): Flow<List<AnimalSighting>> = dao.observeAll()

    fun observe(id: Long): Flow<AnimalSighting?> = dao.observe(id)

    /** Local id of an uploaded entry, from its server id. */
    suspend fun idForRemote(remoteId: String): Long? = dao.idForRemote(remoteId)

    suspend fun getAll(): List<AnimalSighting> = dao.getAll()

    suspend fun add(sighting: AnimalSighting): Long =
        dao.insert(sighting.copy(syncState = SyncState.PENDING)).also { sync.schedule() }

    /**
     * Saves a user edit and marks it for re-upload. An entry that was never uploaded stays PENDING
     * (full upload incl. photo) even if its server id is already reserved; only uploaded ones become DIRTY.
     */
    suspend fun update(sighting: AnimalSighting) {
        val state = if (sighting.syncState == SyncState.PENDING) SyncState.PENDING else SyncState.DIRTY
        dao.update(sighting.copy(syncState = state))
        sync.schedule()
    }

    suspend fun updateLabel(id: Long, label: String, category: String) {
        val s = dao.get(id) ?: return
        update(s.copy(animalLabel = label, category = category))
    }

    suspend fun updateNote(id: Long, note: String?) {
        val s = dao.get(id) ?: return
        update(s.copy(note = note?.takeIf { it.isNotBlank() }))
    }

    /** Favourites are personal and never leave the device, so no sync is needed. */
    suspend fun toggleFavorite(id: Long) {
        val s = dao.get(id) ?: return
        dao.update(s.copy(isFavorite = !s.isFavorite))
    }

    suspend fun delete(id: Long) {
        val s = dao.get(id) ?: return
        dao.delete(s)
        photos.delete(s.photoPath)
        s.remoteId?.let { remoteId ->
            val uid = supabase.currentUserId()
            dao.addPendingDelete(PendingDelete(remoteId, uid?.let { SyncManager.photoPath(it, remoteId) }))
            sync.schedule()
        }
    }
}

/** Deletes photo files kept in app storage; paths outside it are ignored. */
interface PhotoFiles {
    fun delete(path: String)
}

data class StoredPhoto(val path: String, val width: Int, val height: Int)

/** Turns a fresh capture into the stored photo of a find. */
interface PhotoProcessor : PhotoFiles {
    /**
     * Rotates upright (EXIF), downsizes and rewrites [sourcePath] as a clean JPEG in app storage; the
     * source is removed. Cancelling midway leaves no copy behind.
     */
    suspend fun normalize(sourcePath: String): StoredPhoto
}
