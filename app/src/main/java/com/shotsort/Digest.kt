package com.shotsort

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** The once-a-day "here is what I found in your Calendar screenshots" notification. */
object Digest {
    const val ACTION = "com.shotsort.DIGEST"
    private const val CHANNEL = "shotsort_digest"
    private const val NOTIF_ID = 2001

    private fun alarmIntent(c: Context): PendingIntent =
        PendingIntent.getBroadcast(
            c, 0, Intent(c, DigestReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** Arms (or re-arms) the alarm for the next occurrence of the chosen review time. */
    fun schedule(c: Context) {
        val m = Prefs.digestMinutes(c)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, m / 60); set(Calendar.MINUTE, m % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis() + 60_000) cal.add(Calendar.DAY_OF_YEAR, 1)
        try {
            c.getSystemService(AlarmManager::class.java)
                .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, alarmIntent(c))
        } catch (_: Exception) {}
    }

    fun post(c: Context) {
        val items = Proposals.all(c)
        if (items.isEmpty()) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Daily calendar review", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            c, 1, Intent(c, ReviewActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val timed = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault())
        val dayOnly = SimpleDateFormat("EEE d MMM", Locale.getDefault())
        val style = Notification.InboxStyle()
        items.take(6).forEach { style.addLine("${it.title} - ${(if (it.allDay) dayOnly else timed).format(it.start)}") }
        if (items.size > 6) style.setSummaryText("+${items.size - 6} more")
        val n = Notification.Builder(c, CHANNEL)
            .setContentTitle("${items.size} event(s) found in your screenshots")
            .setContentText("Tap to approve or skip each one")
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID, n)
    }

    fun cancel(c: Context) {
        c.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }
}

class DigestReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Digest.ACTION) {
            Digest.post(c)
            Digest.schedule(c)
        }
    }
}
