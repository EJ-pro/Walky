// app/src/main/java/com/example/walky/data/UserRepository.kt
package com.example.walky.data

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val locationText: String = ""
)

data class DogProfile(
    val id: String = "",
    val name: String = "",
    val breed: String = "",
    val age: Int = 0,
    val neutered: Boolean = false,
    val colorHex: String = "#FF8A65" // 기본 주황
)

data class WeeklyStats(
    val walkCount: Int = 0,
    val totalDistanceKm: Double = 0.0,
    val totalMinutes: Int = 0
)

class UserRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun uidOrThrow(): String =
        auth.currentUser?.uid ?: error("로그인이 필요합니다.")

    // --- Realtime listeners ---
    fun listenUser(): Flow<UserProfile?> = callbackFlow {
        val uid = uidOrThrow()
        val reg = db.collection("users").document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(null); return@addSnapshotListener
                }
                val d = snap?.data
                if (d == null) { trySend(null); return@addSnapshotListener }
                trySend(
                    UserProfile(
                        uid = uid,
                        displayName = d["displayName"] as? String ?: "",
                        email = d["email"] as? String ?: "",
                        photoUrl = d["photoUrl"] as? String ?: "",
                        locationText = d["locationText"] as? String ?: ""
                    )
                )
            }
        awaitClose { reg.remove() }
    }

    fun listenDogs(): Flow<List<DogProfile>> = callbackFlow {
        val uid = uidOrThrow()
        val reg = db.collection("users").document(uid)
            .collection("dogs")
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents?.map { doc ->
                    val d = doc.data ?: emptyMap<String, Any>()
                    DogProfile(
                        id = doc.id,
                        name = d["name"] as? String ?: "",
                        breed = d["breed"] as? String ?: "",
                        age = (d["age"] as? Number)?.toInt() ?: 0,
                        neutered = d["neutered"] as? Boolean ?: false,
                        colorHex = d["colorHex"] as? String ?: "#FF8A65"
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun listenWeeklyStats(): Flow<WeeklyStats?> = callbackFlow {
        val uid = uidOrThrow()
        val reg = db.collection("users").document(uid)
            .collection("stats").document("weekly")
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(null); return@addSnapshotListener }
                val d = snap?.data
                if (d == null) { trySend(null); return@addSnapshotListener }
                trySend(
                    WeeklyStats(
                        walkCount = (d["walkCount"] as? Number)?.toInt() ?: 0,
                        totalDistanceKm = (d["totalDistanceKm"] as? Number)?.toDouble() ?: 0.0,
                        totalMinutes = (d["totalMinutes"] as? Number)?.toInt() ?: 0
                    )
                )
            }
        awaitClose { reg.remove() }
    }

    // --- Commands ---
    suspend fun updateUser(displayName: String, locationText: String, photoUrl: String?) {
        val uid = uidOrThrow()
        val data = mutableMapOf<String, Any>(
            "displayName" to displayName,
            "locationText" to locationText
        )
        if (!photoUrl.isNullOrBlank()) data["photoUrl"] = photoUrl
        db.collection("users").document(uid).set(data, SetOptions.merge()).await()
    }

    suspend fun addDog(name: String, breed: String, age: Int, neutered: Boolean) {
        val uid = uidOrThrow()
        db.collection("users").document(uid)
            .collection("dogs")
            .add(
                mapOf(
                    "name" to name,
                    "breed" to breed,
                    "age" to age,
                    "neutered" to neutered,
                    "colorHex" to listOf("#FF8A65","#F06292","#BA68C8","#4DD0E1","#81C784").random()
                )
            ).await()
    }

    suspend fun deleteDog(dogId: String) {
        val uid = uidOrThrow()
        db.collection("users").document(uid)
            .collection("dogs").document(dogId).delete().await()
    }

    fun signOut() = auth.signOut()

    suspend fun updateUserAndAuth(
        displayName: String,
        locationText: String,
        photoUrl: String?,     // Storage URL or any http url
        newEmail: String?      // null if unchanged
    ) {
        val user = auth.currentUser ?: error("로그인이 필요합니다.")

        // 1) Auth: displayName / photoUrl
        if (displayName != (user.displayName ?: "") || !photoUrl.isNullOrBlank()) {
            val updates = userProfileChangeRequest {
                this.displayName = displayName
                if (!photoUrl.isNullOrBlank()) photoUri = Uri.parse(photoUrl)
            }
            user.updateProfile(updates).await()
        }

        // 2) Auth: email (주의: 최근 로그인 필요할 수 있음)
        if (!newEmail.isNullOrBlank() && newEmail != user.email) {
            // 최신 SDK면 아래가 권장 (검증 메일 발송 + 확인 후 반영)
            runCatching { user.verifyBeforeUpdateEmail(newEmail).await() }
                .onFailure {
                    // fallback: 바로 업데이트 시도 (최근 로그인 요구 시 예외 발생)
                    user.updateEmail(newEmail).await()
                }
        }

        // 3) Firestore: 미러링
        val data = mutableMapOf<String, Any>(
            "displayName" to displayName,
            "locationText" to locationText
        )
        if (!photoUrl.isNullOrBlank()) data["photoUrl"] = photoUrl
        if (!newEmail.isNullOrBlank()) data["email"] = newEmail

        db.collection("users").document(user.uid)
            .set(data, SetOptions.merge())
            .await()
    }
}
