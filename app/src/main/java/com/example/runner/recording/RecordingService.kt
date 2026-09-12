package com.example.runner.recording

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.runner.R

/**
 * Minimal foreground service that keeps the app at location-tracking priority
 * while a recording session is active. Contains no logic beyond the notification.
 */
class RecordingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.recording_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(getString(R.string.recording_notification_text))
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        return START_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1
    }
}
