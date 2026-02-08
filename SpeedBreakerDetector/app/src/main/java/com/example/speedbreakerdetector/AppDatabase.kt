package com.example.speedbreakerdetector

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// NEW: Increment the version number from 1 to 2
@Database(entities = [DetectionEvent::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun detectionDao(): DetectionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "speed_breaker_database"
                )
                    // NEW: Add this line to handle the version change during development.
                    // This will clear the database when the version number increases.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
