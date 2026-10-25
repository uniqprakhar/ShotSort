package com.shotsort

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED) {
            Digest.schedule(c)
        }
        if (i.action == Intent.ACTION_BOOT_COMPLETED && Prefs.isWatching(c)) {
            try { ContextCompat.startForegroundService(c, Intent(c, ScreenshotService::class.java)) } catch (_: Exception) {}
        }
    }
}
