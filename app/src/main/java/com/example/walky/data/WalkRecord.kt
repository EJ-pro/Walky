// app/src/main/java/com/example/walky/data/WalkRepository.kt
package com.example.walky.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

data class WalkEntity(
    val uid: String = "",
    val steps: Int = 0,
    val distanceKm: Double = 0.0,
    val durationSec: Long = 0,
    val calories: Int = 0,
    val createdAt: Date? = null
)

class WalkRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private fun userId() = auth.currentUser?.uid ?: ""

    /** 최근 n개 산책 기록 listen (내림차순) */
    fun listenRecentWalks(limit: Long = 10) = callbackFlow<List<WalkEntity>> {
        val uid = userId()
        if (uid.isBlank()) { trySend(emptyList()); close(); return@callbackFlow }

        val reg = db.collection("walks")
            .whereEqualTo("uid", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList()); return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toObject(WalkEntity::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    /** 이번 주(월~일) 합계 한번 계산 */
    suspend fun loadWeeklySummary(): Triple<Int, Double, Long> {
        val uid = userId()
        if (uid.isBlank()) return Triple(0, 0.0, 0)

        // 이번 주 월요일 0시 ~ 다음 주 월요일 0시
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val monday = today.with(java.time.DayOfWeek.MONDAY).atStartOfDay(zone).toInstant()
        val nextMonday = monday.plusSeconds(7 * 24 * 3600)

        val qs = db.collection("walks")
            .whereEqualTo("uid", uid)
            .whereGreaterThanOrEqualTo("createdAt", Date.from(monday))
            .whereLessThan("createdAt", Date.from(nextMonday))
            .get()
            .await()

        var count = 0
        var dist = 0.0
        var dur = 0L
        qs.documents.forEach { d ->
            val w = d.toObject(WalkEntity::class.java) ?: return@forEach
            count += 1
            dist += w.distanceKm
            dur += w.durationSec
        }
        return Triple(count, dist, dur)
    }
}
