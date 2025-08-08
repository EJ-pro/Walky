// MapViewModel.kt
package com.example.walky.ui.screen.map

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.*

data class WalkData(
    val startTime: Long,
    val endTime: Long,
    val path: List<Pair<Double, Double>>,
    val durationSec: Int,
    val distanceKm: Double,
    val stepCount: Int,
    val calorie: Int
)

class MapViewModel : ViewModel() {
    private var timerJob: Job? = null
    private var totalSeconds = 0

    private val _durationText = MutableStateFlow("00:00")
    val durationText: StateFlow<String> = _durationText

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused

    private val _isStarted = MutableStateFlow(false)
    val isStarted: StateFlow<Boolean> = _isStarted

    @SuppressLint("MissingPermission")
    fun startStopwatch() {
        if (_isStarted.value) return
        _isStarted.value = true
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                if (!_isPaused.value) {
                    totalSeconds++
                    _durationText.value = formatTime(totalSeconds)
                }
            }
        }
    }

    fun togglePause() {
        _isPaused.value = !_isPaused.value
    }

    fun resetStopwatch() {
        timerJob?.cancel()
        saveWalkDataToFirebase()
        totalSeconds = 0
        _durationText.value = "00:00"
        _isPaused.value = false
        _isStarted.value = false
        path.clear()
        lastLocation = null
        initialSteps = null
        _distanceKm.value = 0.0
        _calorie.value = 0
        _stepCount.value = 0
        _pathPoints.value = emptyList()
    }

    private val _startPoint = MutableStateFlow<Pair<Double, Double>?>(null)
    val startPoint: StateFlow<Pair<Double, Double>?> = _startPoint
    fun recordStartLocation(lat: Double, lon: Double) {
        _startPoint.value = lat to lon
    }

    private fun formatTime(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return "%02d:%02d".format(m, s)
    }

    // Location & tracking
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val _currentLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val currentLocation: StateFlow<Pair<Double, Double>?> = _currentLocation

    private val path = mutableListOf<Pair<Double, Double>>()
    private val _pathPoints = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val pathPoints: StateFlow<List<Pair<Double, Double>>> = _pathPoints

    private var lastLocation: Pair<Double, Double>? = null
    private val _distanceKm = MutableStateFlow(0.0)
    val distanceKm: StateFlow<Double> = _distanceKm

    private val _calorie = MutableStateFlow(0)
    val calorie: StateFlow<Int> = _calorie

    private var initialSteps: Int? = null
    private val _stepCount = MutableStateFlow(0)
    val stepCount: StateFlow<Int> = _stepCount

    fun onStepChanged(value: Float) {
        if (initialSteps == null) initialSteps = value.toInt()
        _stepCount.value = value.toInt() - (initialSteps ?: 0)
    }

    @SuppressLint("MissingPermission")
    fun startLocationTracking(context: Context) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000).build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(res: LocationResult) {
                res.lastLocation?.let { loc ->
                    val cur = loc.latitude to loc.longitude
                    _currentLocation.value = cur
                    updateTrackingData(cur.first, cur.second)
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
    }
    fun stopLocationTracking() {
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    private fun updateTrackingData(lat: Double, lon: Double) {
        lastLocation?.let { prev ->
            val dist = run {
                val R = 6371.0
                val dLat = Math.toRadians(lat - prev.first)
                val dLon = Math.toRadians(lon - prev.second)
                val a = sin(dLat/2).pow(2.0) +
                        cos(Math.toRadians(prev.first)) *
                        cos(Math.toRadians(lat)) *
                        sin(dLon/2).pow(2.0)
                2 * atan2(sqrt(a), sqrt(1 - a)) * R
            }
            _distanceKm.value += dist
            _calorie.value = (_distanceKm.value * 55).toInt()
        }
        lastLocation = lat to lon
        path.add(lat to lon)
        _pathPoints.value = path.toList()
    }


    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun saveWalkDataToFirebase() {
        val now = System.currentTimeMillis()
        val walk = WalkData(
            startTime = now - totalSeconds * 1_000L,
            endTime = now,
            path = path.toList(),
            durationSec = totalSeconds,
            distanceKm = _distanceKm.value,
            stepCount = _stepCount.value,
            calorie = _calorie.value
        )
        FirebaseFirestore.getInstance()
            .collection("walk_records")
            .add(walk)
    }
}
