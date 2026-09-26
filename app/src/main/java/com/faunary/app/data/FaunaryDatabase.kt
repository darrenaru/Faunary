package com.faunary.app.data

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun detectionsToJson(value: List<Detection>): String = json.encodeToString(value)

    @TypeConverter
    fun detectionsFromJson(value: String): List<Detection> =
        runCatching { json.decodeFromString<List<Detection>>(value) }.getOrDefault(emptyList())
}

@Dao
interface SightingDao {
    @Query("SELECT * FROM animal_sightings ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<AnimalSighting>>

    @Query("SELECT * FROM animal_sightings WHERE id = :id")
    fun observe(id: Long): Flow<AnimalSighting?>

    @Query("SELECT * FROM animal_sightings WHERE id = :id")
    suspend fun get(id: Long): AnimalSighting?

    @Query("SELECT * FROM animal_sightings ORDER BY timestamp DESC")
    suspend fun getAll(): List<AnimalSighting>

    @Insert
    suspend fun insert(sighting: AnimalSighting): Long

    @Update
    suspend fun update(sighting: AnimalSighting)

    @Delete
    suspend fun delete(sighting: AnimalSighting)

    @Query("SELECT * FROM animal_sightings WHERE syncState != 1")
    suspend fun unsynced(): List<AnimalSighting>

    @Query("UPDATE animal_sightings SET remoteId = :remoteId, syncState = :state WHERE id = :id")
    suspend fun markSynced(id: Long, remoteId: String, state: Int = SyncState.SYNCED)

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun addPendingDelete(pending: PendingDelete)

    @Query("SELECT * FROM pending_deletes")
    suspend fun pendingDeletes(): List<PendingDelete>

    @Query("DELETE FROM pending_deletes WHERE remoteId = :remoteId")
    suspend fun clearPendingDelete(remoteId: String)
}

@Database(
    entities = [AnimalSighting::class, PendingDelete::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class FaunaryDatabase : RoomDatabase() {
    abstract fun sightingDao(): SightingDao
}
