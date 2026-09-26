package com.faunary.app.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SightingRepository @Inject constructor(
    private val dao: SightingDao,
    private val photos: PhotoStorage,
) {
    fun observeAll(): Flow<List<AnimalSighting>> = dao.observeAll()

    fun observe(id: Long): Flow<AnimalSighting?> = dao.observe(id)

    suspend fun getAll(): List<AnimalSighting> = dao.getAll()

    suspend fun add(sighting: AnimalSighting): Long = dao.insert(sighting)

    suspend fun update(sighting: AnimalSighting) = dao.update(sighting)

    suspend fun updateLabel(id: Long, label: String, category: String) {
        val s = dao.get(id) ?: return
        dao.update(s.copy(animalLabel = label, category = category))
    }

    suspend fun updateNote(id: Long, note: String?) {
        val s = dao.get(id) ?: return
        dao.update(s.copy(note = note?.takeIf { it.isNotBlank() }))
    }

    suspend fun toggleFavorite(id: Long) {
        val s = dao.get(id) ?: return
        dao.update(s.copy(isFavorite = !s.isFavorite))
    }

    suspend fun delete(id: Long) {
        val s = dao.get(id) ?: return
        dao.delete(s)
        photos.delete(s.photoPath)
    }
}
