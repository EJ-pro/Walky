package com.example.walky.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.walky.data.LocationRepository
import com.example.walky.data.WeatherRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.kakao.sdk.user.UserApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

// 최근 산책 리스트에 보여줄 항목
data class WalkRecord(
    val title: String,
    val date: LocalDateTime,
    val durationMin: Int,
    val distanceKm: Double,
    val calories: Int
)

data class HomeUiState(
    val userName: String = "",
    val profileImageUrl: String? = null,

    // 날씨
    val weatherCity: String = "",
    val tempC: Int = 0,
    val description: String = "",
    val humidity: Int = 0,

    // 오늘 요약 (하루 전체 합산)
    val todaySteps: Int = 0,
    val todayDistanceKm: Double = 0.0,
    val todayDurationMin: Int = 0,
    val stepGoal: Int = 10000,

    // 위치
    val location: Pair<Double, Double>? = null,

    // 최근 산책
    val recentWalks: List<WalkRecord> = emptyList(),

    val isLoading: Boolean = false,
    val error: String? = null,
)

class HomeViewModel(
    private val locRepo: LocationRepository = LocationRepository(),
    private val weatherRepo: WeatherRepository = WeatherRepository()
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var userListener: ListenerRegistration? = null
    private var walksListener: ListenerRegistration? = null
    private var todayListener: ListenerRegistration? = null

    init {
        // 사용자 프로필(닉네임/이미지) 로드
        loadUserProfile()
        // 최근 산책 목록 (오직 리스트만 갱신 — 오늘 합계는 건드리지 않음)
        observeRecentWalks()
    }

    fun refreshAll(context: Context) {
        loadProfileImage(context)
        loadUserName()
        fetchLocationAndWeather(context)
        // 오늘 합계는 startTodayStatsListener가 실시간으로 처리
    }

    /** Firestore: users/{uid}/profile/info.nickname & photoUrl 우선 적용 */
    private fun loadUserProfile() {
        val user = auth.currentUser ?: return
        db.collection("users")
            .document(user.uid)
            .collection("profile")
            .document("info")
            .get()
            .addOnSuccessListener { doc ->
                val nick = doc.getString("nickname")
                    ?: user.displayName.orEmpty()
                _uiState.update { it.copy(userName = nick) }

                doc.getString("photoUrl")?.let { url ->
                    _uiState.update { it.copy(profileImageUrl = url) }
                }
            }
    }

    /** 유저명 단독 로드 (fallback) */
    fun loadUserName() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { snap ->
                val name = snap.getString("displayName")
                if (!name.isNullOrBlank()) {
                    _uiState.update { it.copy(userName = name) }
                }
            }
    }

    /** Google/Kakao/Firebase 사진 순으로 시도 */
    fun loadProfileImage(context: Context) {
        FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()?.let { url ->
            _uiState.update { it.copy(profileImageUrl = url) }
            return
        }
        GoogleSignIn.getLastSignedInAccount(context)?.photoUrl?.toString()?.let { url ->
            _uiState.update { it.copy(profileImageUrl = url) }
            return
        }
        UserApiClient.instance.me { user, _ ->
            user?.kakaoAccount?.profile?.thumbnailImageUrl?.let { url ->
                _uiState.update { it.copy(profileImageUrl = url) }
            }
        }
    }

    /** 위치 & 날씨 fetch */
    fun fetchLocationAndWeather(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val (lat, lon) = locRepo.getCurrentLocation(context)
                _uiState.update { it.copy(location = lat to lon) }

                val res = weatherRepo.fetchCurrentWeatherByCoords(lat, lon)
                val rawDescription = res.weather.firstOrNull()?.description.orEmpty()
                _uiState.update {
                    it.copy(
                        weatherCity = res.name,
                        tempC       = res.main.temp.toInt(),
                        description = simplifyWeather(rawDescription),
                        humidity    = res.main.humidity.toInt(),
                        isLoading   = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    // ✅ 오늘(Asia/Seoul) 범위의 모든 산책을 합산해서 uiState에 반영
    fun startTodayStatsListener() {
        todayListener?.remove()

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val zone = ZoneId.of("Asia/Seoul")
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val startOfTomorrow = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        todayListener = FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("walks")
            .whereGreaterThanOrEqualTo("endedAt", startOfDay)
            .whereLessThan("endedAt", startOfTomorrow)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) return@addSnapshotListener

                var totalSteps = 0
                var totalCalories = 0
                var totalDurationMs = 0L
                var totalDistanceKm = 0.0

                val MAX_REASONABLE = 36L * 60L * 60L * 1000L // 36시간

                for (doc in snap.documents) {
                    // 1) 가능한 경우 endedAt - startedAt를 신뢰
                    val s = doc.getLong("startedAt")
                    val e = doc.getLong("endedAt")
                    val byBounds = if (s != null && e != null && e >= s) e - s else null

                    val raw = doc.getLong("durationMs")
                    val MAX = 36L * 60 * 60 * 1000
                    val byRaw = when {
                        raw == null -> 0L
                        raw in 0..MAX -> raw
                        raw in 0..(36L * 60L * 60L) -> raw * 1000L      // 초로 잘못 저장된 경우
                        raw in 0..(36L * 60L) -> raw * 60_000L         // 분으로 잘못 저장된 경우
                        else -> 0L
                    }

                    val dur = byBounds ?: byRaw
                    totalDurationMs += dur

                    totalSteps      += (doc.getLong("steps") ?: 0L).toInt()
                    totalCalories   += (doc.getLong("calories") ?: 0L).toInt()
                    totalDistanceKm += doc.getDouble("distanceKm")
                        ?: (doc.getLong("distanceKm")?.toDouble() ?: 0.0)
                }

                _uiState.update {
                    it.copy(
                        todaySteps = totalSteps,
                        todayDistanceKm = totalDistanceKm,
                        todayDurationMin = java.util.concurrent.TimeUnit.MILLISECONDS
                            .toMinutes(totalDurationMs).toInt()
                    )
                }
            }
    }

    fun stopTodayStatsListener() {
        todayListener?.remove()
        todayListener = null
    }

    private fun simplifyWeather(description: String): String {
        val lower = description.lowercase()
        return when {
            listOf("clear", "sun", "맑음").any { it in lower } -> "맑음"
            listOf("cloud", "흐림").any { it in lower } -> "흐림"
            listOf("rain", "drizzle", "비").any { it in lower } -> "비"
            listOf("snow", "눈").any { it in lower } -> "눈"
            listOf("storm", "thunder", "번개").any { it in lower } -> "폭풍"
            listOf("fog", "mist", "연무", "안개").any { it in lower } -> "안개"
            else -> "기타"
        }
    }

    /**
     * 최근 산책 목록 (리스트만 갱신)
     * 저장 스키마 가정: users/{uid}/walks/{autoId}
     *  - steps:Int, distanceKm:Double, durationMs:Long, calories:Int
     *  - startedAt:Long(ms), endedAt:Long(ms)
     */
    private fun observeRecentWalks(limit: Long = 10) {
        val uid = auth.currentUser?.uid ?: return
        walksListener?.remove()
        walksListener = db.collection("users")
            .document(uid)
            .collection("walks")
            .orderBy("endedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener

                val zone = ZoneId.systemDefault()
                val records = snap.documents.mapNotNull { d ->
                    val title = d.getString("title") ?: "산책"
                    val endedAt = d.getLong("endedAt") ?: d.getLong("startedAt")
                    val durationMs = d.getLong("durationMs") ?: 0L
                    val distanceKm = d.getDouble("distanceKm") ?: 0.0
                    val calories = (d.getLong("calories") ?: 0L).toInt()

                    val date = (endedAt ?: 0L).let {
                        if (it == 0L) LocalDateTime.now(zone)
                        else LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone)
                    }

                    WalkRecord(
                        title        = title,
                        date         = date,
                        durationMin  = TimeUnit.MILLISECONDS.toMinutes(durationMs).toInt(),
                        distanceKm   = distanceKm,
                        calories     = calories
                    )
                }

                // ✅ 최근 리스트만 업데이트 (오늘 합계는 건드리지 않는다)
                _uiState.update { it.copy(recentWalks = records) }
            }
    }

    override fun onCleared() {
        super.onCleared()
        userListener?.remove()
        walksListener?.remove()
        todayListener?.remove()
    }
}
