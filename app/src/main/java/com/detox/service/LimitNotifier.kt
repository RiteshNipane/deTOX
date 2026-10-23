package com.detox.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.detox.R

/**
 * Nudges when the daily reel limit is approached (50% / 80%, quiet) and a separate, loud
 * alert when it is reached (100%: pops up on screen, makes a sound and vibrates).
 * The two use different channels because Android lets users, not apps, change a channel's
 * importance after it is created.
 */
class LimitNotifier(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    init {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_NUDGE, "Daily limit progress", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REACHED, "Daily limit reached", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pops up when you hit your daily reel limit"
                enableVibration(true)
            }
        )
    }

    fun nudge(level: Int, count: Int, limit: Int) {
        val reached = level >= 100
        val title = if (reached) "Daily limit reached" else context.getString(R.string.app_name)
        val text = when (level) {
            50 -> "Halfway there: $count of $limit reels today."
            80 -> "$count of $limit reels today. Almost at your limit."
            else -> "You've watched $count reels today, your limit was $limit. Time for a break?"
        }
        val n = NotificationCompat.Builder(context, if (reached) CHANNEL_REACHED else CHANNEL_NUDGE)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (reached) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(if (reached) NOTIF_REACHED else NOTIF_NUDGE, n) } // ignored if permission denied
    }

    private companion object {
        const val CHANNEL_NUDGE = "detox_limit"
        const val CHANNEL_REACHED = "detox_limit_reached"
        const val NOTIF_NUDGE = 2
        const val NOTIF_REACHED = 3
    }
}
