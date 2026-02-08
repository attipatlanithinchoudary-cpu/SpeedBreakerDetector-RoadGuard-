package com.example.speedbreakerdetector.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class SpeedBreaker(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)
