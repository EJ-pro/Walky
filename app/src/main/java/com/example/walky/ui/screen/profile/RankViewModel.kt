package com.example.walky.ui.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.walky.gamification.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

data class RankUiState(
    val rankTier: RankTier = RankTier.BRONZE,
    val points14d: Int = 0,
    val nextTierName: String? = RankTier.SILVER.display,
    val toNext: Int = 0,
    val fractionToNext: Float = 0f,
    val todayScore: Int = 0,
    val streakDays: Int = 0,
    val dailyBreakdown: List<Pair<LocalDate, Int>> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

class RankViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()
    private val zone = ZoneId.of("Asia/Seoul")

    private val _ui = MutableStateFlow(RankUiState(loading = true))
    val ui: StateFlow<RankUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val uid = auth.currentUser?.uid ?: run {
            _ui.update { it.copy(loading = false) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val today = LocalDate.now(zone)
                val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val start = today.minusDays(13).atStartOfDay(zone).toInstant().toEpochMilli()

                val qs = db.collection("users").document(uid)
                    .collection("walks")
                    .whereGreaterThanOrEqualTo("endedAt", start)
                    .whereLessThan("endedAt", end)
                    .get().await()

                val byDay = mutableMapOf<LocalDate, MutableList<Map<String, Any?>>>()
                for (d in qs.documents) {
                    val endedAt = (d.getLong("endedAt") ?: continue)
                    val date = Instant.ofEpochMilli(endedAt).atZone(zone).toLocalDate()
                    byDay.getOrPut(date) { mutableListOf() }.add(d.data ?: emptyMap())
                }

                val breakdown = mutableListOf<Pair<LocalDate, Int>>()
                var todayScore = 0
                val activeDays = mutableSetOf<LocalDate>()

                for (i in 0..13) {
                    val day = today.minusDays(13L - i)
                    val items = byDay[day].orEmpty()

                    val totalSteps = items.sumOf { (it["steps"] as? Number)?.toInt() ?: 0 }
                    val totalMin   = items.sumOf { TimeUnit.MILLISECONDS.toMinutes((it["durationMs"] as? Number)?.toLong() ?: 0L).toInt() }
                    val sessionCount20 = items.count {
                        TimeUnit.MILLISECONDS.toMinutes((it["durationMs"] as? Number)?.toLong() ?: 0L) >= 20
                    }
                    val hasPet = items.any { (it["mode"] as? String).orEmpty() == "PET" }

                    val baseScore = calcDailyScore(DailyInputs(totalSteps, totalMin, sessionCount20))
                    val dayScore  = (baseScore * petMultiplier(hasPet)).toInt()
                    breakdown += day to dayScore

                    if (day == today) todayScore = dayScore
                    if (sessionCount20 > 0) activeDays += day
                }

                var streak = 0
                run {
                    var cur = today
                    while (cur in activeDays) { streak++; cur = cur.minusDays(1) }
                }

                val sum14 = breakdown.sumOf { it.second }
                val final14 = (sum14 * streakMultiplier(streak)).toInt()
                val rp = calcRankProgress(final14)

                _ui.update {
                    it.copy(
                        rankTier = rp.tier,
                        points14d = final14,
                        nextTierName = rp.nextTier?.display,
                        toNext = rp.toNext,
                        fractionToNext = rp.fractionToNext,
                        todayScore = todayScore,
                        streakDays = streak,
                        dailyBreakdown = breakdown,
                        loading = false
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message) }
            }
        }
    }
}
