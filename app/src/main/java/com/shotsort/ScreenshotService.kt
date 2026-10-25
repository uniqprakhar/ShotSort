package com.shotsort

import android.app.*
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.*
import android.provider.MediaStore

class ScreenshotService : Service() {
    private var observer: ContentObserver? = null
    private var lastId = 0L

    override fun onCreate() {
        super.onCreate()
        startFg()
        val h = Handler(Looper.getMainLooper())
        observer = object : ContentObserver(h) {
            override fun onChange(self: Boolean, uri: Uri?) {
                super.onChange(self, uri)
                checkLatest()
            }
        }
        contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer!!)
    }
    private fun startFg() {
        val ch = "shotsort"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(ch, "ShotSort", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, ch).setContentTitle("ShotSort watching").setContentText("New screenshots will ask for a folder").setSmallIcon(android.R.drawable.ic_menu_gallery).setContentIntent(pi).build()
        startForeground(1, n)
    }
    private fun checkLatest() {
        try {
            val proj = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.DATE_ADDED)
            contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
                if (!c.moveToFirst()) return
                val id = c.getLong(0)
                if (id == lastId) return
                lastId = id
                val name = c.getString(1) ?: ""
                val rel = c.getString(2) ?: ""
                val added = c.getLong(3)
                val nowSec = System.currentTimeMillis() / 1000
                if (nowSec - added > 60) return
                // Only files that landed in a Screenshots folder (DCIM/Screenshots on Samsung,
                // Pictures/Screenshots on Pixel). Matching on file name alone made sorted copies
                // look like new screenshots.
                val isShot = rel.contains("Screenshot", true)
                if (!isShot || Prefs.isHandled(this, id)) return
                val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                notifySort(uri, name)
            }
        } catch (_: Exception) {}
    }
    private fun notifySort(uri: Uri, name: String) {
        val ch = "shotsort_sort"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(ch, "Sort screenshot", NotificationManager.IMPORTANCE_HIGH))
        val i = Intent(this, SortActivity::class.java).putExtra("uri", uri.toString()).putExtra("name", name)
        val pi = PendingIntent.getActivity(this, (System.currentTimeMillis() % 100000).toInt(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(this, ch).setContentTitle("Sort screenshot?").setContentText(name).setSmallIcon(android.R.drawable.ic_menu_save).setContentIntent(pi).setAutoCancel(true).build()
        nm.notify((System.currentTimeMillis() % 100000).toInt(), n)
    }
    override fun onBind(i: Intent?) = null
    override fun onDestroy() { try { observer?.let { contentResolver.unregisterContentObserver(it) } } catch (_: Exception) {}; super.onDestroy() }
    override fun onStartCommand(i: Intent?, f: Int, s: Int) = START_STICKY
}
