package com.example.speedbreakerdetector

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

class DetectionService : Service(), SensorEventListener {

    // ===============================
    // CONSTANTS
    // ===============================
    private val CHANNEL_ID = "detection_service_channel"
    private val NOTIFICATION_ID = 101
    private val BUMP_THRESHOLD = 4.0f
    private val DETECTION_COOLDOWN_MS = 2500L

    // ===============================
    // SYSTEM COMPONENTS
    // ===============================
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    // ===============================
    // STATE
    // ===============================
    private var lastDetectionTimestamp = 0L

    // ===============================
    // DATABASE
    // ===============================
    private val db by lazy { AppDatabase.getDatabase(this) }

    // ===============================
    // COROUTINES
    // ===============================
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    // ===============================
    // SERVICE LIFECYCLE
    // ===============================
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("DetectionService", "Service started")

        startForeground(NOTIFICATION_ID, createNotification())

        if (accelerometer == null) {
            Log.e("DetectionService", "Accelerometer not available")
            stopSelf()
            return START_NOT_STICKY
        }

        sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI
        )

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        serviceJob.cancel()
        Log.d("DetectionService", "Service destroyed")
    }

    // ===============================
    // SENSOR LISTENER
    // ===============================
    override fun onSensorChanged(event: SensorEvent?) {

        if (event?.sensor?.type != Sensor.TYPE_LINEAR_ACCELERATION) return

        val zAcceleration = event.values[2]
        val currentTime = System.currentTimeMillis()

        if (
            abs(zAcceleration) > BUMP_THRESHOLD &&
            currentTime - lastDetectionTimestamp > DETECTION_COOLDOWN_MS
        ) {
            lastDetectionTimestamp = currentTime
            Log.d("DetectionService", "Speed bump detected: $zAcceleration")
            recordEvent(zAcceleration, "SPEED_BUMP")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }

    // ===============================
    // EVENT RECORDING
    // ===============================
    private fun recordEvent(force: Float, type: String) {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            saveEvent(force, 0.0, 0.0, type)
            return
        }

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            CancellationTokenSource().token
        ).addOnSuccessListener { location ->
            if (location != null) {
                saveEvent(force, location.latitude, location.longitude, type)
            } else {
                saveEvent(force, 0.0, 0.0, type)
            }
        }.addOnFailureListener {
            saveEvent(force, 0.0, 0.0, type)
        }
    }

    private fun saveEvent(
        force: Float,
        latitude: Double,
        longitude: Double,
        type: String
    ) {
        val severity = SeverityUtils.calculateSeverity(force)

        val event = DetectionEvent(
            timestamp = System.currentTimeMillis(),
            force = force,
            latitude = latitude,
            longitude = longitude,
            type = type,
            severity = severity
        )

        serviceScope.launch {
            FirestoreRepository.writeUnvalidatedHazard(
                latitude = event.latitude,
                longitude = event.longitude,
                severity = event.severity,
                type = event.type,
                source = "DETECTED"
            )

            Log.d("DetectionService", "Event saved: $event")
        }
    }

    // ===============================
    // NOTIFICATION
    // ===============================
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Detection Running")
            .setContentText("Monitoring road conditions in background")
            .setSmallIcon(R.drawable.ic_speed_bump)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Detection Service",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Speed breaker detection service"

            val manager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
