package com.example.walky.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.walky.data.LocationRepository
import com.example.walky.data.WeatherRepository
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
    private val locRepo: LocationRepository = LocationRepository(),
    private val weatherRepo: WeatherRepository = WeatherRepository()
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
                val dog = Dog("멍멍이", "골든 리트리버", 3, "https://via.placeholder.com/64", 3)
                val recent = listOf(
                    WalkRecord("한강공원 산책", LocalDateTime.now(), 35, 2.1, 142),
                    WalkRecord("근처 공원 산책", LocalDateTime.now().minusDays(1), 22, 1.3, 89)
                )
                val weekly = WeeklySummary(5, 8.7, 4 * 60 + 12)

                _uiState.update {
                    it.copy(
                        dog = dog,
                        recentWalks = recent,
                        weeklySummary = weekly,
                        isLoading = false
                    )
                }

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    /** 현재 위치 + 날씨 데이터를 함께 가져옴 */
    fun fetchLocationAndWeather(context: Context) {
        viewModelScope.launch {
            try {
                // 1. 현재 위치
                val (lat, lon) = locRepo.getCurrentLocation(context)
                _uiState.update { it.copy(location = lat to lon) }

                // 2. 날씨 API 호출
                val res = weatherRepo.fetchCurrentWeatherByCoords(lat, lon)
                val weather = Weather(
                    city = res.name,
                    tempC = res.main.temp.toInt(),
                    description = res.weather.firstOrNull()?.description ?: "알 수 없음"
                )
                _uiState.update { it.copy(weather = weather) }

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "날씨 가져오기 실패: ${e.localizedMessage}") }
            }
        }
    }

    /** 기존 위치만 가져오는 함수 (필요시 유지) */
    fun fetchLocation(context: Context) {
        viewModelScope.launch {
            val loc = runCatching { locRepo.getCurrentLocation(context) }.getOrNull()
            _uiState.update { it.copy(location = loc) }
        }
    }
}