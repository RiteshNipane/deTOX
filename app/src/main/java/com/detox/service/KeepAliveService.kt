package com.detox.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.detox.R

/** Minimal foreground service so the OS is less likely to kill the tracker process. */
class KeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "deTOX tracking", NotificationManager.IMPORTANCE_MIN)
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Counting reels in the background")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        ServiceCompat.startForeground(this, 1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    private companion object { const val CHANNEL_ID = "detox_keepalive" }
}
