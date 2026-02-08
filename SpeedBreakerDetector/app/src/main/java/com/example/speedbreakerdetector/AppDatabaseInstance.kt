package com.example.speedbreakerdetector

import android.content.Context

object AppDatabaseInstance {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = AppDatabase.getDatabase(context)
            INSTANCE = instance
            instance
        }
    }
}