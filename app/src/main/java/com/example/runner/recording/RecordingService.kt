package com.example.runner.recording

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.runner.R
import com.example.runner.ui.formatElapsed

/** Accent colors matching the trainer colors in the app (text variants). */
private const val RUN_COLOR = 0xFF2E7D32.toInt()
private const val WALK_COLOR = 0xFF1565C0.toInt()

/**
 * Foreground service that keeps the app at location-tracking priority while a
 * recording session is active. Its notification doubles as the lock-screen
 * status: run/walk instruction, heart rate and elapsed session time, updated
 * every second by the activity.
 */
class RecordingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(intent))
        return START_STICKY
    }

    private fun buildNotification(intent: Intent?): android.app.Notification {
        val heartRate = intent?.getIntExtra(EXTRA_HEART_RATE, 0) ?: 0
        val elapsedMs = intent?.getLongExtra(EXTRA_ELAPSED_MS, 0L) ?: 0L
        val trainer = intent?.getStringExtra(EXTRA_TRAINER)

        val (title, color) = when (trainer) {
            "RUN" -> getString(R.string.run_label) to RUN_COLOR
            "WALK" -> getString(R.string.walk_label) to WALK_COLOR
            // First start (no status yet): neutral accent.
            else -> getString(R.string.recording_notification_title) to RUN_COLOR
        }
        val heartText = if (heartRate > 0) heartRate.toString() else "--"
        val text = getString(R.string.status_notification_text, heartText, formatElapsed(elapsedMs))

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setColor(color)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    companion object {
        const val EXTRA_HEART_RATE = "heart_rate"
        const val EXTRA_ELAPSED_MS = "elapsed_ms"
        const val EXTRA_TRAINER = "trainer"

        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1

        /** Creates the status channel. Called from the activity at startup too,
         *  so the channel exists (and is visible in App settings) even before
         *  the service runs. IMPORTANCE_DEFAULT (not LOW) so the notification
         *  is not a "silent" one and shows on the lock screen even when the
         *  device is set to hide silent notifications. Sound/vibration are
         *  disabled because the notification is refreshed every second. */
        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.recording_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}