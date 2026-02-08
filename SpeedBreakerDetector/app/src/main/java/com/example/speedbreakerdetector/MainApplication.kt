package com.example.speedbreakerdetector

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

    }

    private fun createNotificationChannel() {
        // Notification channels are only available on API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Detection Service Channel"
            val descriptionText = "Notification channel for the background detection service."
            val importance = NotificationManager.IMPORTANCE_LOW // Use LOW to avoid sound/vibration
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        // A unique ID for our notification channel
        const val CHANNEL_ID = "DetectionServiceChannel"
    }
}
