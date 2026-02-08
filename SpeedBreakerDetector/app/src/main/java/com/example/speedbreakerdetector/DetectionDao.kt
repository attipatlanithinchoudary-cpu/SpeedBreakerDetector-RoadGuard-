package com.example.speedbreakerdetector

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectionDao {

    /**
     * Inserts a single detection event into the database.
     * If a record with the same primary key already exists, it will be replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: DetectionEvent)

    /**
     * Retrieves all detection events from the database, ordered from newest to oldest.
     * This returns a Flow, which automatically updates the UI when the data changes.
     * Used by the map screen to show markers.
     */
    @Query("SELECT * FROM detection_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<DetectionEvent>>

    /**
     * Retrieves all detection events as a simple List.
     * This is a one-time operation, used by the HistoryActivity to display the full list.
     */
    @Query("SELECT * FROM detection_events ORDER BY timestamp DESC")
    suspend fun getAllEventsList(): List<DetectionEvent>

    /**
     * Deletes all entries from the detection_events table.
     */
    @Query("DELETE FROM detection_events")
    suspend fun clearAllEvents()

    @Query("UPDATE detection_events SET severity = :severity WHERE id = :eventId")
    suspend fun updateSeverity(eventId: Int, severity: String)
    @Query("DELETE FROM detection_events WHERE source = 'REPORTED'")
    suspend fun deleteFirestoreHazards()
    @Query("SELECT * FROM detection_events WHERE id = :eventId LIMIT 1")
    suspend fun getEventById(eventId: Int): DetectionEvent?

}
