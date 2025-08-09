package com.example.walky.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_AVAILABLE
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_UNAVAILABLE
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import java.time.LocalDateTime

class HealthConnectRepository(
    private val zoneId: ZoneId = ZoneId.of("Asia/Seoul")
) {
    fun getClientOrNull(context: Context): HealthConnectClient? {
        val status = HealthConnectClient.getSdkStatus(context, "com.google.android.apps.healthdata")
        return if (status == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else null
    }

    // ✅ 여기! Set<HealthPermission> → Set<String> 로 변경
    val stepPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class)
    )

    suspend fun hasAllPermissions(context: Context): Boolean = withContext(Dispatchers.IO) {
        val client = getClientOrNull(context) ?: return@withContext false
        // ✅ getGrantedPermissions()도 Set<String> 반환이라고 가정
        val granted: Set<String> = client.permissionController.getGrantedPermissions()
        granted.containsAll(stepPermissions)
    }

    suspend fun readTodaySteps(context: Context): Int = withContext(Dispatchers.IO) {
        val client = getClientOrNull(context) ?: return@withContext 0

        val start = LocalDate.now(zoneId).atStartOfDay()
        val end = start.plusDays(1)

        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        (result[StepsRecord.COUNT_TOTAL] ?: 0L).toInt()
    }

    suspend fun readStepsBetween(context: Context, startMs: Long, endMs: Long = System.currentTimeMillis()): Int {
        val client = getClientOrNull(context) ?: return 0

        // 네 프로젝트의 HC 버전에 맞춰 LocalDateTime 기반으로 사용
        val startLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(startMs), zoneId)
        val endLdt   = LocalDateTime.ofInstant(Instant.ofEpochMilli(endMs), zoneId)

        val result = client.aggregate(
            androidx.health.connect.client.request.AggregateRequest(
                metrics = setOf(androidx.health.connect.client.records.StepsRecord.COUNT_TOTAL),
                timeRangeFilter = androidx.health.connect.client.time.TimeRangeFilter.between(startLdt, endLdt)
            )
        )
        return (result[androidx.health.connect.client.records.StepsRecord.COUNT_TOTAL] ?: 0L).toInt()
    }
}