package com.example.walky.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.walky.data.LocationRepository
import com.example.walky.data.WeatherRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.kakao.sdk.user.UserApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    val weatherCity: String = "",
    val tempC: Int = 0,
    val description: String = "",
    val humidity: Int = 0,
    val todaySteps: Int = 0,
    val todayDistanceKm: Double = 0.0,
    val todayDurationMin: Int = 0,
    val stepGoal: Int = 10000,
    val location: Pair<Double, Double>? = null,
    val recentWalks: List<WalkRecord> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class HomeViewModel(
    private val locRepo: LocationRepository = LocationRepository(),
    private val weatherRepo: WeatherRepository = WeatherRepository()
) : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadUserProfile()
        loadTodayStats()
        loadRecentWalks()
    }
    private fun loadRecentWalks() {
        // TODO: Firestore 에서 진짜 불러오실 때 여기를 채워 주세요
        viewModelScope.launch {
            _uiState.update { it.copy(
                recentWalks = listOf(
                    // 예시 더미
                    WalkRecord("예시 산책", LocalDateTime.now(), 30, 2.0, 100)
                )
            ) }
        }
    }
    /** 닉네임·프로필URL 불러오기 */
    private fun loadUserProfile() {
        val user = auth.currentUser ?: return
        db.collection("users")
            .document(user.uid)
            .collection("profile")
            .document("info")
            .get()
            .addOnSuccessListener { doc ->
                // 1) 닉네임은 항상 업데이트
                val nick = doc.getString("nickname") ?: user.displayName.orEmpty()
                _uiState.update { it.copy(userName = nick) }

                // 2) Firestore 에 이미지가 있을 때만 업데이트
                doc.getString("photoUrl")?.let { url ->
                    _uiState.update { it.copy(profileImageUrl = url) }
                }
            }
    }



    /** Google/Kakao 프로필 이미지 fetch (Context 필요) */
    fun loadProfileImage(context: Context) {
        // 1) FirebaseAuth 에서 먼저 시도
        FirebaseAuth.getInstance().currentUser
            ?.photoUrl
            ?.toString()
            ?.let { url ->
                _uiState.update { it.copy(profileImageUrl = url) }
                return
            }
        // Google
        GoogleSignIn.getLastSignedInAccount(context)?.photoUrl
            ?.toString()
            ?.let { url ->
                _uiState.update { it.copy(profileImageUrl = url) }
                return
            }
        // Kakao
        UserApiClient.instance.me { user, _ ->
            user?.kakaoAccount
                ?.profile
                ?.thumbnailImageUrl
                ?.let { url ->
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
                _uiState.update {
                    it.copy(
                        weatherCity    = res.name,
                        tempC          = res.main.temp.toInt(),
                        description    = res.weather.firstOrNull()?.description.orEmpty(),
                        humidity       = res.main.humidity.toInt(),
                        isLoading      = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    /** 오늘 날짜 통계 read */
    fun loadTodayStats() {
        val uid = auth.currentUser?.uid ?: return
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        viewModelScope.launch {
            try {
                val doc = db.collection("users")
                    .document(uid)
                    .collection("dailyStats")
                    .document(today)
                    .get()
                    .await()
                if (doc.exists()) {
                    _uiState.update {
                        it.copy(
                            todaySteps       = doc.getLong("steps")?.toInt() ?: 0,
                            todayDistanceKm  = doc.getDouble("distanceKm") ?: 0.0,
                            todayDurationMin = doc.getLong("durationMin")?.toInt() ?: 0
                        )
                    }
                }
            } catch (_: Exception) { /* ignore */ }
        }
    }

    /** 오늘 날짜 통계 write */
    fun saveTodayStats() {
        val uid   = auth.currentUser?.uid ?: return
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val data = mapOf(
            "steps"        to _uiState.value.todaySteps,
            "distanceKm"   to _uiState.value.todayDistanceKm,
            "durationMin"  to _uiState.value.todayDurationMin,
            "updatedAt"    to FieldValue.serverTimestamp()
        )
        db.collection("users")
            .document(uid)
            .collection("dailyStats")
            .document(today)
            .set(data)
    }
}
