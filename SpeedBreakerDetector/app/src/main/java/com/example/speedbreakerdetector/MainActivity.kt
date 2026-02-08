package com.example.speedbreakerdetector
import android.widget.RadioGroup
import android.widget.Button
import android.Manifest
import android.content.Context
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.Polyline
import android.widget.SeekBar
import android.graphics.Color
import android.content.Intent
import android.widget.TextView
import android.content.pm.PackageManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import android.graphics.Bitmap
import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
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
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import kotlinx.coroutines.flow.collectLatest
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
private val spokenHazardIds = mutableSetOf<Int>()
private const val HAZARD_PASS_DISTANCE_METERS = 15f

private var isVoiceGuidanceEnabled = true
private const val MIN_IMPACT_Z = 5.5f

enum class DetectionStatus {
    IDLE,
    RUNNING,
    STOPPED
}

private var detectionStatus = DetectionStatus.IDLE

private lateinit var elevationApi: ElevationApi

class MainActivity : AppCompatActivity(), OnMapReadyCallback, SensorEventListener {

    private var isFilterVisible = false
    private val spokenHazardIds = mutableSetOf<Int>()
    private val passedHazardIds = mutableSetOf<Int>()


    private var lastLocationForSpeed: android.location.Location? = null
    private var lastSpeedTime = 0L

    private val hazardLastSpokenTime = mutableMapOf<Int, Long>()

    private val HAZARD_VOICE_COOLDOWN_MS = 15_000L


    private var routePoints: List<LatLng> = emptyList()

    private lateinit var binding: HomepageBinding
    private val db by lazy { AppDatabase.getDatabase(this) }
    private lateinit var settingsManager: SettingsManager

    // STEP 3.1 — Filter state (DO NOT change anything else)
    private var filterSpeedBreaker = true
    private var filterPothole = true
    private var selectedFromLatLng: LatLng? = null

    private var filterLow = true
    private var filterMedium = true
    private var filterHigh = true

    private var lastUserLatLng: LatLng? = null

    // --- Navigation state ---
    private var navigationSteps: List<Step> = emptyList()
    private var currentStepIndex = 0
    private var lastAnnouncedStepIndex = -1
    private var isNavigating = false

    private lateinit var expandedSearchLayout: View

    private fun updateStatus(status: DetectionStatus) {
        detectionStatus = status
        binding.statusText.text = when (status) {
            DetectionStatus.IDLE -> "Status: Idle"
            DetectionStatus.RUNNING -> "Status: Detection Running..."
            DetectionStatus.STOPPED -> "Status: Stopped"
        }
    }


    private  val DISPLAY_MIN_Z = 1.5f

    private var lastPositiveSpikeTime = 0L
    private var lastNegativeSpikeTime = 0L

    private var positiveSpikeDetected = false
    private var negativeSpikeDetected = false
    companion object {
        private const val SPIKE_THRESHOLD = 7.5f
        // strength
        private const val MAX_SPIKE_GAP_MS = 180L
        // timing
    }
       // timing



    private var currentSpeedKmh = 0f

    // User-adjustable alert buffer (meters)
    private var alertBufferMeters = 100f

    // Keep track of hazards already announced
    private val alertedHazardIds = mutableSetOf<Int>()


    private val hazardMarkers = mutableListOf<com.google.android.gms.maps.model.Marker>()


    // Map & Location
    private lateinit var googleMap: GoogleMap


    private var lastPeakZ = 0f

    private var routePolyline: com.google.android.gms.maps.model.Polyline? = null

    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    // --- Navigation location updates ---
    private var locationCallback: com.google.android.gms.location.LocationCallback? = null

    private var hasLocationPermission = false

    // Sensor
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastDetectionTimestamp = 0L
    private fun dynamicBumpThreshold(speedKmh: Float): Float {
        return when {
            speedKmh < 15f -> 6.5f
            speedKmh < 30f -> 5.5f
            speedKmh < 50f -> 4.8f
            else -> 4.2f
        }
    }

    private val DETECTION_COOLDOWN_MS = 5000 // 5 seconds

    private val HAZARD_ALERT_DISTANCE_METERS = 100f

    private var consecutiveBumpHits = 0
    private val REQUIRED_HITS = 3


    // Feedback
    private lateinit var vibrator: Vibrator
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)

    private lateinit var directionsApi: DirectionsApi



    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                hasLocationPermission = true
                enableMyLocationOnMap()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }



    // STEP 6.1: Text-to-Speech
    private lateinit var textToSpeech: android.speech.tts.TextToSpeech
    private var isTtsReady = false


    // How many readings to remember
    private val Z_BUFFER_SIZE = 5

    // Stores last Z-axis readings
    private val zBuffer = ArrayDeque<Float>()

    private var lastZ = 0f
    private var lastZTime = 0L
    // --- Stability filter ---
    private var stableStartTime = 0L
    private var lastStableZ = 0f
    private val STABILITY_WINDOW_MS = 300L
    private val STABILITY_THRESHOLD = 0.4f


    private val hazardAnnouncementCount = mutableMapOf<Int, Int>()


    // PERMISSION LAUNCHERS
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startDetectionService()
            } else {
                Toast.makeText(this, "Notification permission is required for background detection.", Toast.LENGTH_LONG).show()
            }
        }

    private fun showReportBottomSheet() {

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_report, null)
        dialog.setContentView(view)

        val typeGroup = view.findViewById<RadioGroup>(R.id.typeGroup)
        val severityGroup = view.findViewById<RadioGroup>(R.id.severityGroup)
        val submitButton = view.findViewById<Button>(R.id.buttonSubmitReport)

        submitButton.setOnClickListener {

            // --- Hazard Type ---
            val type = when (typeGroup.checkedRadioButtonId) {
                R.id.typeSpeedBump -> "SPEED_BUMP"
                R.id.typePothole -> "POTHOLE"
                else -> {
                    Toast.makeText(this, "Select hazard type", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            // --- Severity ---
            val severity = when (severityGroup.checkedRadioButtonId) {
                R.id.severityLow -> "LOW"
                R.id.severityMedium -> "MEDIUM"
                R.id.severityHigh -> "HIGH"
                else -> {
                    Toast.makeText(this, "Select severity", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            // --- Get current location ---
            getCurrentLatLng { latLng ->

                if (latLng == null) {
                    Toast.makeText(this, "Location unavailable", Toast.LENGTH_SHORT).show()
                    return@getCurrentLatLng
                }

                val lat = latLng.latitude
                val lng = latLng.longitude

                // ✅ Save locally (Room)
                val newEvent = DetectionEvent(
                    timestamp = System.currentTimeMillis(),
                    force = 0f,
                    latitude = lat,
                    longitude = lng,
                    type = type,
                    severity = severity,
                    status = "ACTIVE",
                    source = "REPORTED"
                )



                // ✅ Save remotely (Firestore)
                FirestoreRepository.writeUnvalidatedHazard(
                    latitude = latLng.latitude,
                    longitude = latLng.longitude,
                    severity = severity,
                    type = type,
                    source = "REPORTED"
                )

                tryElevationValidation(
                    lat = latLng.latitude,
                    lng = latLng.longitude,
                    type = type,
                    severity = severity
                )




                Toast.makeText(
                    this,
                    "Reported ${type.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }}",
                    Toast.LENGTH_SHORT
                ).show()

                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun tryElevationValidation(lat: Double, lng: Double, type: String, severity: String) {
        validateWithElevation(lat, lng) { isValid ->
            if (isValid) {
                FirestoreRepository.validateHazardWithElevation(lat, lng, type, severity)
            }
        }
    }


    private fun validateWithElevation(
        lat: Double,
        lng: Double,
        onResult: (Boolean) -> Unit
    ) {
        val delta = 0.00005   // ~5 meters
        val locations = "$lat,$lng|${lat + delta},$lng"

        elevationApi.getElevation(locations, getString(R.string.google_maps_key))
            .enqueue(object : retrofit2.Callback<ElevationResponse> {

                override fun onResponse(
                    call: retrofit2.Call<ElevationResponse>,
                    response: retrofit2.Response<ElevationResponse>
                ) {
                    val res = response.body()
                    if (res == null || res.results.size < 2) {
                        onResult(false)
                        return
                    }

                    val h1 = res.results[0].elevation
                    val h2 = res.results[1].elevation

                    val diff = kotlin.math.abs(h1 - h2)

                    // ≥ 8 cm vertical change = real bump
                    onResult(diff >= 0.08)
                }

                override fun onFailure(call: retrofit2.Call<ElevationResponse>, t: Throwable) {
                    onResult(false)
                }
            })
    }


    private fun showWarningBottomSheet() {

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_warning, null)
        dialog.setContentView(view)

        val seekBar = view.findViewById<SeekBar>(R.id.alertDistanceSeekBar)
        val valueText = view.findViewById<TextView>(R.id.alertDistanceValue)

        seekBar.progress = alertBufferMeters.toInt()
        valueText.text = "${alertBufferMeters.toInt()} m"

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                val stepped = ((progress + 12) / 25) * 25
                alertBufferMeters = stepped.toFloat()
                valueText.text = "$stepped m"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        dialog.show()
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        FirestoreRepository.listenToValidatedHazards { hazards ->

            lifecycleScope.launch {

                // Clear old Firestore-synced hazards
                db.detectionDao().deleteFirestoreHazards()

                // Insert only validated hazards
                hazards.forEach {
                    db.detectionDao().insert(it)
                }
            }
        }

        elevationApi = Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ElevationApi::class.java)


        super.onCreate(savedInstanceState)
        binding = HomepageBinding.inflate(layoutInflater)
        setContentView(binding.root)


        locationCallback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {

                val location = locationResult.lastLocation ?: return
                // Ignore poor GPS accuracy
                if (location.accuracy > 25f) return

// Ignore tiny movement (GPS jitter)
                lastLocationForSpeed?.let { lastLoc ->
                    val distance = lastLoc.distanceTo(location)
                    if (distance < 3f) return@onLocationResult
                }

                var gpsSpeed = location.speed * 3.6f
                if (location.accuracy > 25f) return

// Calculate speed from distance if possible
                var calculatedSpeed = gpsSpeed

                lastLocationForSpeed?.let { lastLoc ->
                    val distance = lastLoc.distanceTo(location) // meters
                    val timeDiff = (System.currentTimeMillis() - lastSpeedTime) / 1000f // seconds

                    if (timeDiff > 0) {
                        calculatedSpeed = (distance / timeDiff) * 3.6f // m/s → km/h
                    }
                }

// Blend GPS speed and calculated speed
                val newSpeed = (gpsSpeed * 0.6f) + (calculatedSpeed * 0.4f)

// Smooth final value
                currentSpeedKmh = (currentSpeedKmh * 0.7f) + (newSpeed * 0.3f)
                if (currentSpeedKmh < 3f) currentSpeedKmh = 0f
                binding.speedText.text = "${currentSpeedKmh.toInt()} km/h"

                lastLocationForSpeed = location
                lastSpeedTime = System.currentTimeMillis()




                val userLatLng = LatLng(
                    location.latitude,
                    location.longitude
                )

                // 🔑 THIS CONNECTS NAVIGATION
                checkNavigationProgress(userLatLng)
            }
        }


        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.google_maps_key))
        }




// Single autocomplete fragment (used for TO)
        val toAutocomplete =
            supportFragmentManager.findFragmentById(R.id.toAutocomplete)
                    as AutocompleteSupportFragment

        toAutocomplete.setHint("Choose destination")

        toAutocomplete.setPlaceFields(
            listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG
            )
        )

        toAutocomplete.setOnPlaceSelectedListener(object : PlaceSelectionListener {

            override fun onPlaceSelected(place: Place) {
                val destination = place.latLng ?: return

                getCurrentLatLng { from ->
                    if (from != null) {
                        drawRoute(from, destination)
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Current location unavailable",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                binding.expandedSearchLayout.visibility = View.GONE
                binding.collapsedSearchLayout.visibility = View.VISIBLE
            }

            override fun onError(status: Status) {
                Log.e("PLACES_ERROR", status.toString())
                Toast.makeText(
                    this@MainActivity,
                    "Place error: $status",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })



        directionsApi = Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DirectionsApi::class.java)

        expandedSearchLayout = binding.expandedSearchLayout

        binding.collapsedSearchLayout.setOnClickListener {

            // Hide collapsed bar
            binding.collapsedSearchLayout.visibility = View.GONE

            // Show expanded From → To UI
            binding.expandedSearchLayout.visibility = View.VISIBLE

            // Set FROM as current location
            binding.fromLocationText.text = "Current location"
        }


        binding.buttonWarning.setOnClickListener {
            showWarningBottomSheet()
        }


        binding.filterToggleButton.setOnClickListener {
            if (binding.filterPanel.visibility == View.GONE) {
                binding.filterPanel.visibility = View.VISIBLE
            } else {
                binding.filterPanel.visibility = View.GONE
            }
        }
        binding.zoomInFab.setOnClickListener {
            if (::googleMap.isInitialized) {
                googleMap.animateCamera(CameraUpdateFactory.zoomIn())
            }
        }

        binding.zoomOutFab.setOnClickListener {
            if (::googleMap.isInitialized) {
                googleMap.animateCamera(CameraUpdateFactory.zoomOut())
            }
        }


        binding.filterPanel.visibility = View.GONE


        // STEP 3.5: Connect filter checkboxes to state variables
        binding.filterSpeedBreaker.setOnCheckedChangeListener { _, isChecked ->
            filterSpeedBreaker = isChecked
            addMarkersToMap()
        }

        binding.filterPothole.setOnCheckedChangeListener { _, isChecked ->
            filterPothole = isChecked
            addMarkersToMap()
        }

        binding.filterLow.setOnCheckedChangeListener { _, isChecked ->
            filterLow = isChecked
            addMarkersToMap()
        }

        binding.filterMedium.setOnCheckedChangeListener { _, isChecked ->
            filterMedium = isChecked
            addMarkersToMap()
        }

        binding.filterHigh.setOnCheckedChangeListener { _, isChecked ->
            filterHigh = isChecked
            addMarkersToMap()
        }

        binding.recenterFab.setOnClickListener {
            recenterToCurrentLocation()
        }


        // -- INITIALIZE MANAGERS AND CLIENTS --
        settingsManager = SettingsManager(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        initializeVibrator()

        textToSpeech = android.speech.tts.TextToSpeech(this) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                textToSpeech.language = java.util.Locale.US
                isTtsReady = true
            }
        }

        // -- MAPVIEW INITIALIZATION --
        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)



        // -- CHECK PERMISSIONS --
        checkLocationPermission()

        // -- SET UP BUTTONS --
        binding.startButton.setOnClickListener { startDetection() }
        binding.stopButton.setOnClickListener { stopDetection() }
        binding.viewHistoryButton.setOnClickListener {
            addMarkersToMap()
            Toast.makeText(this, "Map refreshed with latest hazards.", Toast.LENGTH_SHORT).show()
        }
        binding.buttonReportPothole.setOnClickListener {
            showReportBottomSheet()
        }

        checkLocationPermission()


    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        googleMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(20.5937, 78.9629),
                17f
            )
        )
        googleMap.setOnMapClickListener {
            binding.expandedSearchLayout.visibility = View.GONE
            binding.collapsedSearchLayout.visibility = View.VISIBLE
        }

        // STEP 1: Marker click listener (feedback popup)
        googleMap.setOnMarkerClickListener { marker ->

            android.app.AlertDialog.Builder(this)
                .setTitle(marker.title)
                .setMessage("Is this severity correct?")

                .setNeutralButton("Remove Hazard") { _, _ ->

                    val eventId = marker.tag as? Int ?: return@setNeutralButton

                    voteToRemoveHazard(eventId)
                }

                .setPositiveButton("More Severe") { _, _ ->
                    val eventId = marker.tag as? Int ?: return@setPositiveButton
                    lifecycleScope.launch {
                        db.detectionDao().updateSeverity(eventId, "HIGH")
                        addMarkersToMap() // redraw markers with updated color
                    }
                }

                .setNegativeButton("Less Severe") { _, _ ->
                    val eventId = marker.tag as? Int ?: return@setNegativeButton
                    lifecycleScope.launch {
                        db.detectionDao().updateSeverity(eventId, "LOW")
                        addMarkersToMap() // redraw markers with updated color
                    }
                }


                .show()

            true
        }
        enableMyLocationOnMap()

        addMarkersToMap()
    }
    private fun enableMyLocationOnMap() {
        if (!::googleMap.isInitialized) return

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap.isMyLocationEnabled = true

            googleMap.uiSettings.isMyLocationButtonEnabled = true
            googleMap.uiSettings.isZoomControlsEnabled = false
            googleMap.uiSettings.isCompassEnabled = true
        }
    }



    private fun drawRoute(from: LatLng, to: LatLng) {

        Log.e("ROUTE_DEBUG", "FROM = ${from.latitude},${from.longitude}")
        Log.e("ROUTE_DEBUG", "TO = ${to.latitude},${to.longitude}")


        if (from.latitude == to.latitude && from.longitude == to.longitude) {
            Toast.makeText(this, "Destination is same as origin", Toast.LENGTH_SHORT).show()
            return
        }

        val origin = "${from.latitude},${from.longitude}"
        val destination = "${to.latitude},${to.longitude}"
        alertedHazardIds.clear()

        directionsApi.getRoute(
            origin = origin,
            destination = destination,
            apiKey = getString(R.string.google_maps_key)
        )
            .enqueue(object : retrofit2.Callback<DirectionsResponse> {

                override fun onResponse(
                    call: retrofit2.Call<DirectionsResponse>,
                    response: retrofit2.Response<DirectionsResponse>
                ) {
                    Log.e("ROUTE_DEBUG", "HTTP CODE: ${response.code()}")

                    if (!response.isSuccessful) {
                        Log.e("ROUTE_DEBUG", "NOT SUCCESSFUL")
                        Log.e("ROUTE_DEBUG", "ERROR BODY: ${response.errorBody()?.string()}")
                        return
                    }

                    val body = response.body()
                    Log.e("ROUTE_DEBUG", "FULL BODY: $body")

                    if (body == null) {
                        Log.e("ROUTE_DEBUG", "BODY IS NULL")
                        return
                    }

                    if (body.routes.isEmpty()) {
                        Toast.makeText(this@MainActivity, "No route found", Toast.LENGTH_SHORT).show()
                        Log.e("ROUTE_DEBUG", "ROUTES EMPTY → ZERO_RESULTS")
                        return
                    }
                    navigationSteps = body.routes[0].legs.firstOrNull()?.steps ?: emptyList()

                    currentStepIndex = 0
                    lastAnnouncedStepIndex = -1

                    val route = body.routes[0]
                    val polylinePoints = route.overviewPolyline?.points

                    if (polylinePoints == null) {

                        val fallbackPoints = mutableListOf<LatLng>()

                        for (step in route.legs[0].steps) {
                            val stepPoints = PolylineUtils.decode(step.polyline.points)
                            fallbackPoints.addAll(stepPoints)
                        }
                        routePoints = fallbackPoints

                        if (fallbackPoints.isEmpty()) return

                        routePolyline?.remove()

                        routePolyline = googleMap.addPolyline(
                            PolylineOptions()
                                .addAll(fallbackPoints)
                                .width(12f)
                                .color(Color.BLUE)
                        )
                        detectElevationBumps(routePoints)


                    } else {

                        val points = PolylineUtils.decode(polylinePoints)
                        routePoints = points

                        routePolyline?.remove()

                        routePolyline = googleMap.addPolyline(
                            PolylineOptions()
                                .addAll(points)
                                .width(12f)
                                .color(Color.BLUE)
                        )
                    }

                    startNavigation()

                }


                override fun onFailure(call: retrofit2.Call<DirectionsResponse>, t: Throwable) {
                Log.e("ROUTE_DEBUG", "FAILED", t)
                Toast.makeText(this@MainActivity, "Route failed", Toast.LENGTH_SHORT).show()
            }
        })
    }
    private fun getStepEndLatLng(step: Step): LatLng {
        val points = PolylineUtils.decode(step.polyline.points)
        return points.last()
    }

    private fun isHazardNearRoute(
        hazard: LatLng,
        route: List<LatLng>,
        thresholdMeters: Float = 30f
    ): Boolean {
        for (point in route) {
            val distance = distanceInMeters(
                hazard.latitude,
                hazard.longitude,
                point.latitude,
                point.longitude
            )
            if (distance <= thresholdMeters) return true
        }
        return false
    }


    private fun stopNavigation() {
        isNavigating = false
        routePoints = emptyList()
        stopLocationUpdates()
    }


    private fun startNavigation() {
        hazardLastSpokenTime.clear()
        spokenHazardIds.clear()
        passedHazardIds.clear()


        if (navigationSteps.isEmpty()) return
        isNavigating = true
        alertedHazardIds.clear()

        startLocationUpdates()   // ✅ ADD THIS
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            alertedHazardIds.clear()

        }
    }

    private fun startLocationUpdates() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val locationRequest =
            com.google.android.gms.location.LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                2000L   // every 2 seconds
            ).build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            android.os.Looper.getMainLooper()
        )
    }

    private fun voteToRemoveHazard(eventId: Int) {

        lifecycleScope.launch {

            val event = db.detectionDao().getEventById(eventId) ?: return@launch

            FirestoreRepository.voteRemoveHazard(
                latitude = event.latitude,
                longitude = event.longitude,
                type = event.type
            )

            Toast.makeText(this@MainActivity, "Removal vote submitted", Toast.LENGTH_SHORT).show()
        }
    }


    private fun detectElevationBumps(routePoints: List<LatLng>) {
        var lastBumpPoint: LatLng? = null


        if (routePoints.isEmpty()) return

        // Take every 10th point to avoid too many API calls
        val sampledPoints = routePoints.filterIndexed { index, _ ->
            index % 10 == 0
        }

        // Build locations string
        val locations = sampledPoints.joinToString("|") {
            "${it.latitude},${it.longitude}"
        }

        elevationApi.getElevation(locations, getString(R.string.google_maps_key))
            .enqueue(object : retrofit2.Callback<ElevationResponse> {

                override fun onResponse(
                    call: retrofit2.Call<ElevationResponse>,
                    response: retrofit2.Response<ElevationResponse>
                ) {

                    val body = response.body() ?: return
                    val results = body.results

                    if (results.size < 2) return

                    // Detect bumps
                    for (i in 1 until results.size - 1) {

                        val diffUp = results[i].elevation - results[i - 1].elevation
                        val diffDown = results[i + 1].elevation - results[i].elevation

                        if (diffUp > 0.20 && diffDown < -0.20) {

                            val bumpLocation = sampledPoints[i]

                            if (lastBumpPoint != null) {

                                val distance = distanceInMeters(
                                    lastBumpPoint!!.latitude,
                                    lastBumpPoint!!.longitude,
                                    bumpLocation.latitude,
                                    bumpLocation.longitude
                                )

                                if (distance < 30) continue
                            }

                            googleMap.addMarker(
                                MarkerOptions()
                                    .position(bumpLocation)
                                    .title("Speed bump (Elevation)")
                            )

                            lastBumpPoint = bumpLocation
                        }
                    }

                }

                override fun onFailure(call: retrofit2.Call<ElevationResponse>, t: Throwable) {
                    Log.e("ELEVATION", "Failed to fetch elevation", t)
                }
            })
    }

    private fun addMarkersToMap() {
        googleMap ?: return
        val bumpIcon = bitmapDescriptorFromVector(R.drawable.ic_speed_bump)
        val potholeIcon = bitmapDescriptorFromVector(R.drawable.ic_pothole)
        lifecycleScope.launch {
            db.detectionDao().getAllEvents().collectLatest { events ->
                hazardMarkers.forEach { it.remove() }
                hazardMarkers.clear()


                for (event in events) {
// Show only hazards on current route
                    if (routePoints.isNotEmpty()) {
                        val hazardLatLng = LatLng(event.latitude, event.longitude)
                        if (!isHazardNearRoute(hazardLatLng, routePoints)) {
                            continue
                        }
                    }

                    // Skip invalid coordinates
                    if (event.latitude == 0.0 && event.longitude == 0.0) continue

                    // -------- TYPE FILTER --------
                    if (event.type == "SPEED_BUMP" && !filterSpeedBreaker) continue
                    if (event.type == "POTHOLE" && !filterPothole) continue

                    // -------- SEVERITY FILTER --------


                    val icon = when (event.severity) {
                        "HIGH" -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                        "MEDIUM" -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)
                        else -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    }




                    val title = getDisplayTitle(event)

                    val position = LatLng(event.latitude, event.longitude)

                    val marker = googleMap.addMarker(
                        MarkerOptions()
                            .position(position)
                            .title(title)
                            .snippet("${event.source.lowercase().replaceFirstChar { it.uppercase() }} • Severity: ${event.severity}")
                            .icon(icon)
                    )

                    marker?.let {
                        it.tag = event.id
                        hazardMarkers.add(it)
                    }


                    marker?.tag = event.id

                }

                generatePrediction(events)
            }
        }
    }

    private fun startDetection() {
        if (!hasLocationPermission) {
            Toast.makeText(this, "Cannot start detection without location permission.", Toast.LENGTH_LONG).show()
            checkLocationPermission()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                startDetectionService()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startDetectionService()
        }
    }

    private fun startDetectionService() {
        val intent = Intent(this, DetectionService::class.java)
        ContextCompat.startForegroundService(this, intent)
        updateStatus(DetectionStatus.RUNNING)
    }


    private fun stopDetection() {
        stopService(Intent(this, DetectionService::class.java))
        updateStatus(DetectionStatus.STOPPED)
    }

    private fun showSeverityDialog(type: String) {
        val severities = arrayOf("LOW", "MEDIUM", "HIGH")

        android.app.AlertDialog.Builder(this)
            .setTitle("Select Severity")
            .setItems(severities) { _, index ->
                val fakeForce = when (index) {
                    0 -> 2f     // LOW
                    1 -> 5f     // MEDIUM
                    else -> 8f  // HIGH
                }
                recordEvent(fakeForce, type)
            }
            .show()
    }

    private fun speak(text: String) {
        if (isTtsReady) {
            textToSpeech.speak(
                text,
                android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                null,
                null
            )
        }
    }


    private fun recordEvent(force: Float, type: String) {

        val severity = SeverityUtils.calculateSeverity(force)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            saveEventToDb(
                force = force,
                latitude = 0.0,
                longitude = 0.0,
                type = type,
                severity = severity
            )
            return
        }

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            CancellationTokenSource().token
        ).addOnSuccessListener { location ->

            val lat = location?.latitude ?: 0.0
            val lng = location?.longitude ?: 0.0

            saveEventToDb(
                force = force,
                latitude = lat,
                longitude = lng,
                type = type,
                severity = severity
            )

            // Firestore sync
            FirestoreRepository.writeUnvalidatedHazard(
                latitude = lat,
                longitude = lng,
                severity = severity,
                type = type,
                source = "REPORTED"
            )

        }.addOnFailureListener {

            saveEventToDb(
                force = force,
                latitude = 0.0,
                longitude = 0.0,
                type = type,
                severity = severity
            )
        }
    }


    private fun getDisplayTitle(event: DetectionEvent): String {
        val typeText = event.type
            .replace("_", " ")
            .lowercase()
            .replaceFirstChar { it.uppercase() }

        return when (event. source) {
            "REPORTED" -> "Reported $typeText"
            else -> "Detected $typeText"
        }
    }
    private fun recenterToCurrentLocation() {

        if (!::googleMap.isInitialized) return

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        val cancellationToken = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationToken.token
        ).addOnSuccessListener { location ->

            if (location != null) {
                val latLng = LatLng(location.latitude, location.longitude)

                // ✅ Update stored location
                lastUserLatLng = latLng

                // ✅ Google Maps–style zoom & center
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(latLng, 18f)
                )
            } else {
                Toast.makeText(this, "Unable to get current location", Toast.LENGTH_SHORT).show()
            }

        }.addOnFailureListener {
            Toast.makeText(this, "Location fetch failed", Toast.LENGTH_SHORT).show()
        }
    }



    private fun saveEventToDb(
        force: Float,
        latitude: Double,
        longitude: Double,
        type: String,
        severity: String
    ) {

        val newEvent = DetectionEvent(
            timestamp = System.currentTimeMillis(),
            force = force,
            latitude = latitude,
            longitude = longitude,
            type = type,
            severity = severity,
            status = "ACTIVE",
            source = "DETECTED"
        )


    }

    // STEP 5.2: Helper function to calculate distance between two coordinates
    private fun distanceInMeters(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            lat1, lng1,
            lat2, lng2,
            results
        )
        return results[0]
    }

    // STEP 5.3: Check if user is near any stored hazard
    private fun checkNearbyHazards(
        userLat: Double,
        userLng: Double,
        events: List<DetectionEvent>
    ) {
        val now = System.currentTimeMillis()

        for (event in events) {

            // Ignore invalid coordinates
            if (event.latitude == 0.0 && event.longitude == 0.0) continue

            // Only speed bumps
            if (event.type != "SPEED_BUMP") continue

            val distance = distanceInMeters(
                userLat, userLng,
                event.latitude,
                event.longitude
            )

            // 🚫 Mark as passed if very close
            if (distance <= HAZARD_PASS_DISTANCE_METERS) {
                passedHazardIds.add(event.id)
                continue
            }

            // 🚫 Never speak again once passed
            if (passedHazardIds.contains(event.id)) continue

            // ⏱ Cooldown check (THIS IS THE KEY FIX)
            val lastSpoken = hazardLastSpokenTime[event.id] ?: 0L
            if (now - lastSpoken < HAZARD_VOICE_COOLDOWN_MS) continue

            // 🔊 Speak only when entering alert zone
            if (distance <= alertBufferMeters) {

                hazardLastSpokenTime[event.id] = now

                // Non-voice feedback
                triggerFeedback()

                // Voice (ONCE per cooldown)
                if (isTtsReady && isVoiceGuidanceEnabled) {
                    textToSpeech.speak(
                        "Speed bump ahead",
                        android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                        null,
                        null
                    )
                }

                break
            }
        }
    }






    private fun checkNavigationProgress(userLatLng: LatLng) {

        if (!isNavigating || navigationSteps.isEmpty()) return

        if (currentStepIndex >= navigationSteps.size) {
            speak("You have arrived at your destination")
            stopNavigation()
            return
        }

        val step = navigationSteps[currentStepIndex]
        val stepEnd = getStepEndLatLng(step)

        val distance = distanceInMeters(
            userLatLng.latitude,
            userLatLng.longitude,
            stepEnd.latitude,
            stepEnd.longitude
        )
        lifecycleScope.launch {
            db.detectionDao().getAllEvents().collectLatest { events ->
                checkNearbyHazards(
                    userLatLng.latitude,
                    userLatLng.longitude,
                    events
                )
            }
        }


        if (distance < 50 && lastAnnouncedStepIndex != currentStepIndex) {

            val instruction = android.text.Html.fromHtml(
                step.html_instructions,
                android.text.Html.FROM_HTML_MODE_LEGACY
            ).toString()

            speak(instruction)

            lastAnnouncedStepIndex = currentStepIndex
            currentStepIndex++
        }
    }


    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {

            val zAcceleration = event.values[2]
            // 📱 Track phone stability
            if (abs(zAcceleration - lastStableZ) < STABILITY_THRESHOLD) {
                if (stableStartTime == 0L) stableStartTime = System.currentTimeMillis()
            } else {
                stableStartTime = 0L
            }
            lastStableZ = zAcceleration

            val now = System.currentTimeMillis()

            val dz = abs(zAcceleration - lastZ)
            val dt = maxOf(1L, now - lastZTime)
            val jerk = dz / dt     // how sudden the shock is

            lastZ = zAcceleration
            lastZTime = now

            // 🚫 Ignore hand movement & slow tilting
            if (abs(zAcceleration) < 2.5f || jerk < 0.03f) {
                positiveSpikeDetected = false
                negativeSpikeDetected = false
                return
            }


            lastPeakZ = maxOf(lastPeakZ, abs(zAcceleration))



// 🚫 Ignore hand movement & slow tilting
            if (abs(zAcceleration) < 2.5f || jerk < 0.03f) {
                positiveSpikeDetected = false
                negativeSpikeDetected = false
                return
            }


// ⬆️ sudden upward spike
            if (zAcceleration > dynamicBumpThreshold(currentSpeedKmh)) {

                positiveSpikeDetected = true
                lastPositiveSpikeTime = now
            }

// ⬇️ sudden downward spike
            if (zAcceleration < -dynamicBumpThreshold(currentSpeedKmh)) {

                negativeSpikeDetected = true
                lastNegativeSpikeTime = now
            }


            if (kotlin.math.abs(zAcceleration) >= DISPLAY_MIN_Z) {
                binding.detectionHistoryText.text =
                    "Real-Time Sensor Logs:\nZ-axis: ${"%.2f".format(zAcceleration)} m/s²"
                binding.detectionHistoryText.visibility = View.VISIBLE
            }


            val currentTime = System.currentTimeMillis()

            if (detectionStatus == DetectionStatus.RUNNING &&
                currentSpeedKmh >= 5f &&
                positiveSpikeDetected &&
                negativeSpikeDetected &&
                abs(lastPositiveSpikeTime - lastNegativeSpikeTime) <= MAX_SPIKE_GAP_MS
            ) {
                if (System.currentTimeMillis() - stableStartTime < STABILITY_WINDOW_MS) return

                if (jerk < 0.015f) return

                consecutiveBumpHits++
            } else {
                consecutiveBumpHits = 0
            }

            if (consecutiveBumpHits >= REQUIRED_HITS &&
                currentTime - lastDetectionTimestamp > DETECTION_COOLDOWN_MS
            ) {


                // ⏱ update timestamp
                lastDetectionTimestamp = currentTime
                binding.statusText.text = "Status: SPEED BUMP DETECTED!"

                // ✅ 1. CREATE impactForce HERE
                // ✅ 1. CREATE impactForce HERE
                val impactForce = lastPeakZ
                lastPeakZ = 0f

// 🚫 Ignore very small impacts (noise / soft bumps)
                if (impactForce < MIN_IMPACT_Z) {
                    positiveSpikeDetected = false
                    negativeSpikeDetected = false
                    consecutiveBumpHits = 0
                    return
                }


                // ✅ 2. CREATE severity HERE (THIS WAS MISSING)
                val severity = calculateSmartSeverity(
                    impactZ = impactForce,
                    speedKmh = currentSpeedKmh
                )

                // 🔐 3. Permission check
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }

                // 📍 4. Get last location
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { loc ->

                        if (loc == null) return@addOnSuccessListener

                        FirestoreRepository.writeUnvalidatedHazard(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            severity = severity,
                            type = "SPEED_BUMP",
                            source = "DETECTED"
                        )
                    }

            }
        }
    }



    // CORRECTED: Added the missing onAccuracyChanged function
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used, but required for the SensorEventListener interface
    }

    // -- FEEDBACK AND PERMISSION METHODS --
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

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                hasLocationPermission = true
                enableMyLocationOnMap()
            }

            shouldShowRequestPermissionRationale(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }

            else -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }


    private fun initializeVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun bitmapDescriptorFromVector(@DrawableRes vectorResId: Int): BitmapDescriptor? {
        return ContextCompat.getDrawable(this, vectorResId)?.run {
            setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            val bitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
            draw(Canvas(bitmap))
            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }

    private fun calculateSmartSeverity(
        impactZ: Float,
        speedKmh: Float
    ): String {

        val score = impactZ * (speedKmh / 20f)

        return when {
            score >= 35 -> "HIGH"
            score >= 20 -> "MEDIUM"
            else -> "LOW"
        }
    }

    // STEP 8.2: Simple hazard prediction logic
    private fun generatePrediction(events: List<DetectionEvent>) {

        if (events.isEmpty()) {
            binding.predictionText.visibility = View.GONE
            return
        }

        // Group hazards by type
        val groupedByType = events.groupBy { it.type }

        var bestType: String? = null
        var bestSeverity = "LOW"
        var bestCount = 0

        for ((type, list) in groupedByType) {
            if (list.size > bestCount) {
                bestCount = list.size
                bestType = type

                // Take highest severity seen
                bestSeverity = when {
                    list.any { it.severity == "HIGH" } -> "HIGH"
                    list.any { it.severity == "MEDIUM" } -> "MEDIUM"
                    else -> "LOW"
                }


            }
        }

        // Only predict if multiple hazards exist
        if (bestCount < 2 || bestType == null) {
            binding.predictionText.visibility = View.GONE
            return
        }

        // Simple confidence calculation
        val confidence = (bestCount * 25).coerceAtMost(100)

        binding.predictionText.text =
            "Prediction: $bestSeverity ${bestType.replace("_", " ")} ahead (Confidence: $confidence%)"

        binding.predictionText.visibility = View.VISIBLE
    }

    // STEP 7.3: Helper to get current location for routing


    // -- ACTIVITY LIFECYCLE MANAGEMENT FOR MAPVIEW AND SENSORS --
    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()

        // ✅ Only add markers if map is ready
        if (::googleMap.isInitialized) {
            addMarkersToMap()
        }

        accelerometer?.also { accel ->
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
        }
    }


    fun validateHazardWithElevation(
        lat: Double,
        lng: Double,
        type: String,
        severity: String
    ) {
        val key = "${lat.toString().take(5)}_${lng.toString().take(5)}_$type"
        val ref = Firebase.firestore.collection("hazards").document(key)

        // This will be called AFTER elevation = true
        ref.update("validated", true)
    }


    fun writeUnvalidatedHazard(
        latitude: Double,
        longitude: Double,
        severity: String,
        type: String,
        source: String
    ) {
        val key = "${latitude.toString().take(5)}_${longitude.toString().take(5)}_$type"
        val ref = Firebase.firestore.collection("hazards").document(key)

        Firebase.firestore.runTransaction { tx ->
            val snap = tx.get(ref)
            val count = if (snap.exists()) snap.getLong("count") ?: 0 else 0

            tx.set(ref, mapOf(
                "latitude" to latitude,
                "longitude" to longitude,
                "type" to type,
                "severity" to severity,
                "count" to count + 1,
                "validated" to false
            ), SetOptions.merge())
        }
    }



    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        binding.mapView.onDestroy()
    }


    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }

    private fun getCurrentLatLng(callback: (LatLng?) -> Unit) {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            callback(null)
            return
        }







        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)

                    // ✅ ADD THIS LINE
                    lastUserLatLng = latLng

                    callback(latLng)
                } else {
                    callback(null)
                }
            }

            .addOnFailureListener {
                callback(null)
            }

    }

}
