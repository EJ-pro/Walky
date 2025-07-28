// src/main/java/com/example/walky/data/LocationRepository.kt
package com.example.walky.data

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationRepository {
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Pair<Double, Double> =
        suspendCancellableCoroutine { cont ->
            val client = LocationServices.getFusedLocationProviderClient(context)
            client.lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        cont.resume(loc.latitude to loc.longitude)
                    } else {
                        cont.resumeWithException(Exception("위치 정보를 가져올 수 없습니다."))
                    }
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
            cont.invokeOnCancellation { /* 취소 시 특별 처리 필요 시 */ }
        }
}
