package com.faunary.app.data

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
}

@Database(entities = [AnimalSighting::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class FaunaryDatabase : RoomDatabase() {
    abstract fun sightingDao(): SightingDao
}
