package com.example.speedbreakerdetector

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "detection_events")
data class DetectionEvent(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val timestamp: Long,

    val details: String = "Unknown Event",

    val force: Float,

    val latitude: Double,

    val longitude: Double,

    // Type of hazard (SPEED_BUMP / POTHOLE)
    val type: String = "SPEED_BUMP",
    @ColumnInfo(name = "reportCount")
    val reportCount: Int = 1,


    val source: String = "DETECTED",

    val severity: String = "LOW",

    val status: String = "ACTIVE"

)
