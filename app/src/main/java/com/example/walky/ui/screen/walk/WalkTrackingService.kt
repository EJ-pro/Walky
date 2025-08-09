// app/src/main/java/com/example/walky/tracking/WalkTrackingService.kt
package com.example.walky.walk

import android.app.*
import android.content.*
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.walky.R

class WalkTrackingService : Service() {
    companion object {
        const val ACTION_START = "walky.START"
        const val ACTION_STOP  = "walky.STOP"
        private const val CH_ID = "walky_tracking"
        private const val NOTI_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel(CH_ID, "Walk tracking", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTI_ID, buildNotification("산책 기록 중"))
                // TODO: 여기서 FusedLocationProviderClient 시작 + HC 폴링 시작
            }
            ACTION_STOP -> {
                // TODO: 위치 업데이트/폴링 중지
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(R.drawable.ic_walk)   // 아이콘 준비
            .setContentTitle("Walky")
            .setContentText(text)
            .setOngoing(true)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null
}
