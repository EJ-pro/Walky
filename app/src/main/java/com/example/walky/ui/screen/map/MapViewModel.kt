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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

data class WalkPoint(
    val lat: Double,
    val lon: Double,
    val t: Long // epoch millis
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

    private var sensorManager: SensorManager? = null
    private var stepCounterSensor: Sensor? = null
    private var stepDetectorSensor: Sensor? = null
    private var baseStepCount: Float? = null        // TYPE_STEP_COUNTER 기준값
    private var useSensorSteps: Boolean = false     // 센서 사용 가능 여부

    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!_isStarted.value || _isPaused.value) return
            when (event.sensor.type) {
                Sensor.TYPE_STEP_COUNTER -> {
                    if (baseStepCount == null) baseStepCount = event.values[0]
                    val delta = (event.values[0] - (baseStepCount ?: event.values[0])).toInt()
                    _stepCount.value = delta.coerceAtLeast(0)
                }
                Sensor.TYPE_STEP_DETECTOR -> {
                    // 보조: 디바이스에 따라 둘 중 하나만 들어오는 경우가 있어요
                    _stepCount.value = _stepCount.value + 1
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
    fun startStepSensors(context: Context) {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        var registered = false
        stepCounterSensor?.let {
            registered = sensorManager?.registerListener(stepListener, it, SensorManager.SENSOR_DELAY_NORMAL) == true
        }
        stepDetectorSensor?.let {
            sensorManager?.registerListener(stepListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        useSensorSteps = registered || (stepDetectorSensor != null)
        if (useSensorSteps) {
            baseStepCount = null // 시작할 때 기준 리셋
        }
    }

    fun stopStepSensors() {
        sensorManager?.unregisterListener(stepListener)
        baseStepCount = null
        useSensorSteps = false
    }
    private fun isPlausibleStep(distMeters: Double, dtMs: Long, accuracy: Float?): Boolean {
        // 정확도 나쁠 때 버림
        if (accuracy != null && accuracy > 25f) return false
        if (dtMs <= 0) return false

        // 초당 속도 3.0m/s(시속 10.8km) 이상이면 보행으로 보기 어려움 → 점프로 보고 버림
        val speed = distMeters / (dtMs / 1000.0)
        if (speed > 3.0) return false

        // 너무 작은 미세 이동은 버림 (GPS jitter 제거)
        if (distMeters < 3.0) return false

        return true
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

        // ✅ 필터 적용
        if (!isPlausibleStep(distMeters, dtMs, acc)) return

        // append
        _pathPoints.value = current + nowPoint
        val newKm = _distanceKm.value + (distMeters / 1000.0)
        _distanceKm.value = newKm

        updateStepsFromDistanceIfNeeded()
        updateCalories()
    }
    /** 거리 → 걸음수 추정은 센서가 없을 때만 */
    private fun updateStepsFromDistanceIfNeeded() {
        if (useSensorSteps) return
        val meters = _distanceKm.value * 1000.0
        _stepCount.value = (meters / 0.78).roundToInt()
    }

    // simple calorie estimate: 60 kcal / km (walking)
    private fun updateCalories() {
        _calorie.value = (_distanceKm.value * 60.0).roundToInt()
    }

    /** Optional: derive steps from distance (avg step length ~0.78m). Replace with Sensor later if needed. */
    private fun updateStepsFromDistance() {
        val meters = _distanceKm.value * 1000.0
        _stepCount.value = (meters / 0.78).roundToInt()
    }

    // ----- public API for MapScreen -----

    fun recordStartLocation(lat: Double, lon: Double) {
        _startPoint.value = lat to lon
        _pathPoints.value = listOf(lat to lon)
        lastLocation = Location("start").apply {
            latitude = lat
            longitude = lon
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
        if (_isPaused.value) {
            // resume
            _isPaused.value = false
            pausedAccumulatedMs += System.currentTimeMillis() - lastPauseAt
        } else {
            // pause
            _isPaused.value = true
            lastPauseAt = System.currentTimeMillis()
        }
    }

    fun resetStopwatch() {
        _isStarted.value = false
        _isPaused.value = false
        timerJob?.cancel()
        timerJob = null
        startedAt = 0L
        pausedAccumulatedMs = 0L
        lastPauseAt = 0L

        // keep path/distance for summary until saved; call clearWalk() after save if you want
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

    fun startLocationTracking(context: Context) {
        if (fusedClient == null) fusedClient = LocationServices.getFusedLocationProviderClient(context)
        if (locationCallback != null) return

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!_isStarted.value || _isPaused.value) return
                result.lastLocation?.let { handleNewLocation(it) }
            }
        }

        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L)
            .setMinUpdateIntervalMillis(1000L)
            .setMinUpdateDistanceMeters(10.0f)
            .setWaitForAccurateLocation(true)
            .build()

        try {
            fusedClient!!.requestLocationUpdates(req, locationCallback!!, context.mainLooper)
        } catch (_: SecurityException) {}
    }


    fun stopLocationTracking() {
        locationCallback?.let { fusedClient?.removeLocationUpdates(it) }
        locationCallback = null
    }

    fun clearWalk() {
        _pathPoints.value = emptyList()
        _startPoint.value = null
        _distanceKm.value = 0.0
        _stepCount.value = 0
        _calorie.value = 0
        _durationText.value = "00:00:00"
    }

    // ----- save to Firebase -----

    @SuppressLint("MissingPermission")
    fun endWalkAndSave(context: Context, mode: String = "MY", onDone: (Boolean) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val endedAt = System.currentTimeMillis()

        if (uid == null) {
            android.util.Log.e("MapVM", "SAVE BLOCKED: uid=null (로그인 필요)")
            onDone(false); return
        }
        if (startedAt == 0L) {
            android.util.Log.e("MapVM", "SAVE BLOCKED: startedAt=0 (startStopwatch() 호출됐는지 확인)")
            onDone(false); return
        }

        val durationMillis = calcFinalDurationMs(endedAt)
        val now = endedAt
        val points = _pathPoints.value.map { (lat, lon) -> WalkPoint(lat, lon, now) }

        android.util.Log.d("MapVM",
            "SAVE TRY uid=$uid, points=${points.size}, distKm=${"%.3f".format(_distanceKm.value)}, " +
                    "steps=${_stepCount.value}, cal=${_calorie.value}, durMs=$durationMillis"
        )

        val data = hashMapOf(
            "title" to defaultTitle(startedAt, endedAt),
            "mode" to mode,
            "startedAt" to startedAt,
            "endedAt" to endedAt,
            "durationMs" to durationMillis,
            "durationMin" to java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(durationMillis).toInt(),
            "distanceKm" to _distanceKm.value,
            "steps" to _stepCount.value,
            "calories" to _calorie.value,
            "path" to points.map { mapOf("lat" to it.lat, "lon" to it.lon, "t" to it.t) }
        )

        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("walks").add(data)
            .addOnSuccessListener {
                android.util.Log.d("MapVM", "SAVE OK")
                onDone(true)
            }
            .addOnFailureListener { e ->
                android.util.Log.e("MapVM", "SAVE FAIL: ${e.message}", e)
                onDone(false)
            }
    }


    // ✅ 일관된 지속시간 계산(정지시간 반영, 이상치 보정)
    private fun calcFinalDurationMs(endedAt: Long): Long {
        val effectivePaused = if (_isPaused.value && lastPauseAt != 0L)
            pausedAccumulatedMs + (endedAt - lastPauseAt)
        else pausedAccumulatedMs

        val raw = (endedAt - startedAt - effectivePaused).coerceAtLeast(0L)

        // 혹시라도 이상치(예: 36시간 초과)는 컷
        val MAX = 36L * 60 * 60 * 1000
        return raw.coerceAtMost(MAX)
    }


    private fun elapsedMs(endedAt: Long): Long {
        val total = endedAt - startedAt
        val paused = if (_isPaused.value) pausedAccumulatedMs + (System.currentTimeMillis() - lastPauseAt) else pausedAccumulatedMs
        return total - paused
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
    }
}
