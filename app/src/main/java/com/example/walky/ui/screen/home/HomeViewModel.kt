package com.example.walky.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.walky.data.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class Weather(val city: String, val tempC: Int, val description: String)
data class Dog(val name: String, val breed: String, val age: Int, val avatarUrl: String, val activityLevel: Int)
data class WalkRecord(val title: String, val date: LocalDateTime, val durationMin: Int, val distanceKm: Double, val calories: Int)
data class WeeklySummary(val count: Int, val totalDistanceKm: Double, val totalTimeMin: Int)

data class HomeUiState(
    val weather: Weather? = null,
    val dog: Dog? = null,
    val recentWalks: List<WalkRecord> = emptyList(),
    val weeklySummary: WeeklySummary? = null,
    val location: Pair<Double, Double>? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

class HomeViewModel(
    private val locRepo: LocationRepository = LocationRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadAll()
    }

    private fun loadAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // TODO: 실제 데이터로 교체
                val weather = Weather("강남구", 22, "맑음")
                val dog = Dog("멍멍이", "골든 리트리버", 3, "https://via.placeholder.com/64", 3)
                val recent = listOf(
                    WalkRecord("한강공원 산책", LocalDateTime.now(), 35, 2.1, 142),
                    WalkRecord("근처 공원 산책", LocalDateTime.now().minusDays(1), 22, 1.3, 89)
                )
                val weekly = WeeklySummary(5, 8.7, 4 * 60 + 12)

                _uiState.value = HomeUiState(
                    weather = weather,
                    dog = dog,
                    recentWalks = recent,
                    weeklySummary = weekly,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    /** 권한 승인 후 호출: Context를 전달받아 현재 위치를 가져옵니다 */
    fun fetchLocation(context: Context) {
        viewModelScope.launch {
            val loc = runCatching { locRepo.getCurrentLocation(context) }.getOrNull()
            _uiState.update { it.copy(location = loc) }
        }
    }
}
