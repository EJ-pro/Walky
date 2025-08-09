package com.example.walky.ui.screen.map

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import com.example.walky.data.health.HealthConnectRepository

data class WalkPoint(
    val lat: Double,
    val lon: Double,
    val t: Long
)

class MapViewModel : ViewModel() {

    // ----- UI state -----
    private val _isStarted = MutableStateFlow(false)
    val isStarted: StateFlow<Boolean> = _isStarted.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _stepCount = MutableStateFlow(0)
    val stepCount: StateFlow<Int> = _stepCount.asStateFlow()

    private val _distanceKm = MutableStateFlow(0.0)
    val distanceKm: StateFlow<Double> = _distanceKm.asStateFlow()

    private val _durationText = MutableStateFlow("00:00:00")
    val durationText: StateFlow<String> = _durationText.asStateFlow()

    private val _calorie = MutableStateFlow(0)
    val calorie: StateFlow<Int> = _calorie.asStateFlow()

    private val _pathPoints = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val pathPoints: StateFlow<List<Pair<Double, Double>>> = _pathPoints.asStateFlow()

    private val _startPoint = MutableStateFlow<Pair<Double, Double>?>(null)
    val startPoint: StateFlow<Pair<Double, Double>?> = _startPoint.asStateFlow()

    // ----- location / timer -----
    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var lastLocation: Location? = null

    private var timerJob: Job? = null
    private var startedAt: Long = 0L
    private var pausedAccumulatedMs: Long = 0L
    private var lastPauseAt: Long = 0L

    // ----- Health Connect steps -----
    private val hcRepo = HealthConnectRepository()
    private var healthStepsJob: Job? = null
    private var useHealthSteps: Boolean = false

    // 세션 기준 및 일시정지 보정
    private var baseStepsToday: Int? = null      // 세션 시작 시점의 “오늘 총걸음”
    private var lastPolledToday: Int = 0         // 최근 폴링한 “오늘 총걸음”
    private var pauseStartToday: Int? = null     // 일시정지 시작 시의 “오늘 총걸음”

    // kcal 대략치
    private fun updateCalories() {
        _calorie.value = (_distanceKm.value * 60.0).roundToInt()
    }

    /** HC 미사용 시에만 거리→걸음 환산 */
    private fun updateStepsFromDistanceIfNeeded() {
        if (useHealthSteps) return
        val meters = _distanceKm.value * 1000.0
        _stepCount.value = (meters / 0.78).roundToInt()
    }

    // ----- public API -----

    fun recordStartLocation(lat: Double, lon: Double) {
        _startPoint.value = lat to lon
        _pathPoints.value = listOf(lat to lon)
        lastLocation = Location("start").apply {
            latitude = lat
            longitude = lon
            time = System.currentTimeMillis()
        }
    }

    fun startStopwatch() {
        if (_isStarted.value) return
        _isStarted.value = true
        _isPaused.value = false
        startedAt = System.currentTimeMillis()
        pausedAccumulatedMs = 0L
        runTimer()
    }

    fun togglePause() {
        if (!_isStarted.value) return
        val now = System.currentTimeMillis()
        if (_isPaused.value) {
            // 재시작: 일시정지 중 걸음 제외를 위해 base 보정
            _isPaused.value = false
            pausedAccumulatedMs += now - lastPauseAt
            pauseStartToday?.let { ps ->
                val deltaPauseSteps = (lastPolledToday - ps).coerceAtLeast(0)
                if (useHealthSteps && baseStepsToday != null) {
                    baseStepsToday = baseStepsToday!! + deltaPauseSteps
                }
            }
            pauseStartToday = null
        } else {
            // 일시정지: 현재 today를 기록
            _isPaused.value = true
            lastPauseAt = now
            if (useHealthSteps) pauseStartToday = lastPolledToday
        }
    }

    fun resetStopwatch() {
        _isStarted.value = false
        _isPaused.value = false
        timerJob?.cancel(); timerJob = null
        startedAt = 0L
        pausedAccumulatedMs = 0L
        lastPauseAt = 0L

        // 세션 값 초기화
        stopHealthStepsPolling()
        baseStepsToday = null
        pauseStartToday = null
        lastPolledToday = 0
        _stepCount.value = 0
    }

    private fun runTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_isStarted.value) {
                if (!_isPaused.value) {
                    val now = System.currentTimeMillis()
                    val elapsed = now - startedAt - pausedAccumulatedMs
                    _durationText.value = formatHMS(elapsed)
                }
                delay(1000)
            }
        }
    }

    // ----- Health Connect: 세션 0부터 올라가게 폴링 -----

    fun startHealthStepsPolling(context: Context, intervalMs: Long = 5000L) {
        healthStepsJob?.cancel()
        viewModelScope.launch {
            useHealthSteps = hcRepo.hasAllPermissions(context)
            if (!useHealthSteps || startedAt == 0L) return@launch

            // ✅ 세션 시작 시점의 “오늘 총걸음”을 기준으로 저장하고, 즉시 화면 0으로 초기화
            baseStepsToday = hcRepo.readTodaySteps(context)
            _stepCount.value = 0

            healthStepsJob = viewModelScope.launch {
                while (_isStarted.value) {
                    if (!_isPaused.value) {
                        val today = hcRepo.readTodaySteps(context)
                        lastPolledToday = today

                        // 기준이 없다면(드물게 1회차 실패) 지금 값을 기준으로 세팅
                        if (baseStepsToday == null) baseStepsToday = today

                        val sessionSteps = (today - (baseStepsToday ?: today)).coerceAtLeast(0)
                        _stepCount.value = sessionSteps
                    }
                    delay(intervalMs)
                }
            }
        }
    }

    fun stopHealthStepsPolling() {
        healthStepsJob?.cancel()
        healthStepsJob = null
    }

    // ----- location -----

    fun startLocationTracking(context: Context) {
        if (fusedClient == null) fusedClient = LocationServices.getFusedLocationProviderClient(context)
        if (locationCallback != null) return

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!_isStarted.value || _isPaused.value) return
                val loc = result.lastLocation ?: return
                handleNewLocation(loc)
            }
        }

        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L)
            .setMinUpdateIntervalMillis(1000L)
            .setMinUpdateDistanceMeters(3.0f)
            .setWaitForAccurateLocation(true)
            .build()

        try {
            fusedClient!!.requestLocationUpdates(req, locationCallback!!, context.mainLooper)
        } catch (_: SecurityException) { }
    }

    fun stopLocationTracking() {
        locationCallback?.let { fusedClient?.removeLocationUpdates(it) }
        locationCallback = null
    }

    private fun handleNewLocation(loc: Location) {
        val prev = lastLocation
        lastLocation = loc

        val nowPoint = loc.latitude to loc.longitude
        val current = _pathPoints.value
        if (current.isEmpty()) {
            _pathPoints.value = listOf(nowPoint)
            if (_startPoint.value == null) _startPoint.value = nowPoint
            return
        }

        val distMeters = if (prev != null) prev.distanceTo(loc).toDouble() else 0.0
        val dtMs = if (prev != null) (loc.time - prev.time) else 1000L
        val acc = if (loc.hasAccuracy()) loc.accuracy else null

        if (!isValidMove(distMeters, dtMs, acc)) return

        _pathPoints.value = current + nowPoint
        _distanceKm.value = _distanceKm.value + (distMeters / 1000.0)
        updateStepsFromDistanceIfNeeded()
        updateCalories()
    }

    private fun isValidMove(dist: Double, dtMs: Long, accuracy: Float?): Boolean {
        if (accuracy != null && accuracy > 25f) return false
        if (dtMs <= 0) return false
        val speed = dist / (dtMs / 1000.0)
        if (speed > 3.0) return false
        if (dist < 3.0) return false
        return true
    }

    fun clearWalk() {
        _pathPoints.value = emptyList()
        _startPoint.value = null
        _distanceKm.value = 0.0
        _calorie.value = 0
        _durationText.value = "00:00:00"
        // _stepCount은 resetStopwatch에서 0으로
    }

    // ----- save -----

    @SuppressLint("MissingPermission")
    fun endWalkAndSave(context: Context, mode: String = "MY", onDone: (Boolean) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) { onDone(false); return }

        val endedAt = System.currentTimeMillis()
        if (startedAt == 0L) { onDone(false); return }

        val durationMillis = calcFinalDurationMs(endedAt)
        val durationMinutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(durationMillis).toInt()

        val now = endedAt
        val points: List<WalkPoint> = _pathPoints.value.map { (lat, lon) ->
            WalkPoint(lat, lon, now)
        }

        val data = hashMapOf(
            "title" to defaultTitle(startedAt, endedAt),
            "mode" to mode,
            "startedAt" to startedAt,
            "endedAt" to endedAt,
            "durationMs" to durationMillis,
            "durationMin" to durationMinutes,
            "distanceKm" to _distanceKm.value,
            "steps" to _stepCount.value,        // 세션 걸음 그대로 저장
            "calories" to _calorie.value,
            "path" to points.map { mapOf("lat" to it.lat, "lon" to it.lon, "t" to it.t) }
        )

        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("walks").add(data)
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }

    private fun calcFinalDurationMs(endedAt: Long): Long {
        val effectivePaused = if (_isPaused.value && lastPauseAt != 0L)
            pausedAccumulatedMs + (endedAt - lastPauseAt)
        else pausedAccumulatedMs
        val raw = (endedAt - startedAt - effectivePaused).coerceAtLeast(0L)
        val MAX = 36L * 60 * 60 * 1000
        return raw.coerceAtMost(MAX)
    }

    private fun defaultTitle(start: Long, end: Long): String {
        val z = ZoneId.systemDefault()
        val fmt = DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(z)
        return "Walk ${fmt.format(Instant.ofEpochMilli(start))} - ${fmt.format(Instant.ofEpochMilli(end))}"
    }

    private fun formatHMS(ms: Long): String {
        val totalSec = (ms / 1000).toInt()
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationTracking()
        timerJob?.cancel()
        stopHealthStepsPolling()
    }
}
