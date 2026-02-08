package com.example.speedbreakerdetector.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SpeedBreakerDao {

    @Insert
    suspend fun insert(speedBreaker: SpeedBreaker)

    @Query("SELECT * FROM SpeedBreaker")
    suspend fun getAll(): List<SpeedBreaker>
}
