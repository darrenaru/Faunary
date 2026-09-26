package com.faunary.app.data

import com.faunary.app.remote.SupabaseProvider
import com.faunary.app.remote.SyncManager
import com.faunary.app.remote.SyncScheduler
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SightingRepository @Inject constructor(
    private val dao: SightingDao,
    private val photos: PhotoStorage,
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

    /** Saves a user edit and marks it for re-upload. */
    suspend fun update(sighting: AnimalSighting) {
        dao.update(sighting.copy(syncState = if (sighting.remoteId != null) SyncState.DIRTY else SyncState.PENDING))
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
