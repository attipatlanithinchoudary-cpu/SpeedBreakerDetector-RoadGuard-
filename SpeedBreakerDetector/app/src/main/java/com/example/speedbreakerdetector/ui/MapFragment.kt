package com.example.speedbreakerdetector.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.speedbreakerdetector.AppDatabase
import com.example.speedbreakerdetector.DetectionEvent
import com.example.speedbreakerdetector.DetectionService
import com.example.speedbreakerdetector.R
import com.example.speedbreakerdetector.SettingsManager
import com.example.speedbreakerdetector.databinding.HomepageBinding
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.CancellationTokenSource
// These imports are correct for the 'places:3.4.0' dependency
// CORRECTED IMPORTS FOR 'places:3.4.0' - 'libraries' IS REQUIRED
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: HomepageBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { AppDatabase.getDatabase(requireContext()) }

    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(requireActivity()) }
    private lateinit var settingsManager: SettingsManager
    private lateinit var vibrator: Vibrator
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
    private var hasLocationPermission = false
    private var googleMap: GoogleMap? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startDetectionService()
            } else {
                Toast.makeText(requireContext(), "Notification permission is required to run detection in the background.", Toast.LENGTH_LONG).show()
            }
        }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                hasLocationPermission = true
                Toast.makeText(requireContext(), "Location permission granted", Toast.LENGTH_SHORT).show()
            } else {
                hasLocationPermission = false
                Toast.makeText(requireContext(), "Location permission denied. Location will not be recorded.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = HomepageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager(requireContext())
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)

        checkLocationPermission()


        binding.startButton.setOnClickListener { startDetection() }
        binding.stopButton.setOnClickListener { stopDetection() }
        binding.viewHistoryButton.setOnClickListener {
            addMarkersToMap()
            Toast.makeText(requireContext(), "Map refreshed with latest hazards.", Toast.LENGTH_SHORT).show()
        }
        binding.buttonReportPothole.setOnClickListener {
            recordEvent(0.0f, "POTHOLE")
            Toast.makeText(requireContext(), "Pothole reported at current location!", Toast.LENGTH_SHORT).show()
        }
    }


    private fun triggerFeedback() {
        if (settingsManager.isVibrationEnabled()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        }
        if (settingsManager.isSoundEnabled()) {
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 200)
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(20.5937, 78.9629), 4f))
        addMarkersToMap()
    }

    private fun bitmapDescriptorFromVector(@DrawableRes vectorResId: Int): BitmapDescriptor? {
        return context?.let {
            val vectorDrawable = ContextCompat.getDrawable(it, vectorResId)
            vectorDrawable?.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
            val bitmap = Bitmap.createBitmap(vectorDrawable!!.intrinsicWidth, vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            vectorDrawable.draw(canvas)
            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }

    private fun addMarkersToMap() {
        googleMap ?: return

        lifecycleScope.launch {
            db.detectionDao().getAllEvents().collectLatest { events ->
                googleMap?.clear()

                for (event in events) {
                    if (event.latitude != 0.0 && event.longitude != 0.0) {

                        val position = LatLng(event.latitude, event.longitude)

                        val title = event.type
                            .replace("_", " ")
                            .lowercase(Locale.ROOT)
                            .replaceFirstChar { it.titlecase(Locale.ROOT) }

                        // NEW: choose marker color based on severity
                        val markerColor = when (event.severity) {
                            "HIGH" -> BitmapDescriptorFactory.HUE_RED
                            "MEDIUM" -> BitmapDescriptorFactory.HUE_ORANGE
                            else -> BitmapDescriptorFactory.HUE_GREEN
                        }



                        googleMap?.addMarker(
                            MarkerOptions()
                                .position(position)
                                .title(title)
                                .snippet(
                                    "Force: ${"%.2f".format(event.force)} | Severity: ${event.severity}"
                                )
                                .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
                        )
                    }
                }
            }
        }
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                hasLocationPermission = true
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            else -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun startDetection() {
        if (!hasLocationPermission) {
            Toast.makeText(requireContext(), "Cannot start detection without location permission.", Toast.LENGTH_LONG).show()
            checkLocationPermission()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                startDetectionService()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startDetectionService()
        }
    }

    private fun startDetectionService() {
        val intent = Intent(requireContext(), DetectionService::class.java)
        ContextCompat.startForegroundService(requireContext(), intent)
        binding.statusText.text = "Status: Detection Running..."
        Toast.makeText(requireContext(), "Detection service started.", Toast.LENGTH_SHORT).show()
    }

    private fun stopDetection() {
        val intent = Intent(requireContext(), DetectionService::class.java)
        requireContext().stopService(intent)
        binding.statusText.text = "Status: Stopped"
        Toast.makeText(requireContext(), "Detection service stopped.", Toast.LENGTH_SHORT).show()
    }

    private fun recordEvent(force: Float, type: String) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "Location permission not available for recording event.", Toast.LENGTH_SHORT).show()
            saveEvent(force, 0.0, 0.0, type)
            return
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    saveEvent(force, location.latitude, location.longitude, type)
                    triggerFeedback()
                } else {
                    Toast.makeText(requireContext(), "Could not get location. Saving without it.", Toast.LENGTH_SHORT).show()
                    saveEvent(force, 0.0, 0.0, type)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to get location: ${e.message}", Toast.LENGTH_SHORT).show()
                saveEvent(force, 0.0, 0.0, type)
            }
    }

    private fun saveEvent(force: Float, latitude: Double, longitude: Double, type: String) {
        val newEvent = DetectionEvent(
            timestamp = System.currentTimeMillis(),
            force = force,
            latitude = latitude,
            longitude = longitude,
            type = type
        )

    }

    // --- Lifecycle Methods ---
    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        addMarkersToMap()
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.mapView.onDestroy()
        _binding = null
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }
}
