package com.shotsort

import android.Manifest
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private var pollScheduled = false

    private val pickModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            toast("Importing the model file, this can take a minute...")
            Thread {
                val ok = ModelStore.importFrom(applicationContext, uri)
                runOnUiThread { toast(if (ok) "Smart titles are on" else "That file could not be used"); render() }
            }.start()
        }
    }
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        list = findViewById(R.id.folderList)
        findViewById<Button>(R.id.btnStart).setOnClickListener { ensurePermsAndStart() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopService(Intent(this, ScreenshotService::class.java)); Prefs.setWatching(this, false); toast("ShotSort stopped") }
        findViewById<Button>(R.id.btnAdd).setOnClickListener {
            val input = findViewById<EditText>(R.id.inputFolder)
            val name = input.text.toString()
            if (name.isBlank()) { toast("Type a folder name first"); return@setOnClickListener }
            Prefs.add(this, name); input.text.clear(); render()
        }
        findViewById<Button>(R.id.btnReview).setOnClickListener { startActivity(Intent(this, ReviewActivity::class.java)) }
        findViewById<Button>(R.id.btnDigestTime).setOnClickListener {
            val m = Prefs.digestMinutes(this)
            TimePickerDialog(this, { _, h, min ->
                Prefs.setDigestMinutes(this, h * 60 + min)
                Digest.schedule(this)
                render()
            }, m / 60, m % 60, false).show()
        }
        findViewById<Button>(R.id.btnModel).setOnClickListener {
            when (ModelStore.status(this).state) {
                ModelStore.State.MISSING -> AlertDialog.Builder(this)
                    .setTitle("Download the title model?")
                    .setMessage("About 1 GB, one time. Use Wi-Fi. After this, titles are written on your phone with no internet.")
                    .setPositiveButton("Download") { _, _ -> ModelStore.startDownload(this); render() }
                    .setNegativeButton("Cancel", null)
                    .show()
                ModelStore.State.READY -> { ModelStore.remove(this); render() }
                else -> {}
            }
        }
        findViewById<Button>(R.id.btnPick).setOnClickListener { pickModel.launch(arrayOf("*/*")) }
        render(); ensurePermsAndStart(auto = true)
    }
    override fun onResume() { super.onResume(); render() }
    private fun render() {
        list.removeAllViews()
        for (f in Prefs.load(this)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val tv = TextView(this).apply { text = "\uD83D\uDCC1  Pictures/$f"; textSize = 16f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
            val del = Button(this).apply { text = "X"; setOnClickListener { Prefs.remove(this@MainActivity, f); render() } }
            row.addView(tv); row.addView(del); list.addView(row)
        }
        val m = Prefs.digestMinutes(this)
        val h12 = if (m / 60 % 12 == 0) 12 else m / 60 % 12
        findViewById<Button>(R.id.btnDigestTime).text = "Daily review: %d:%02d %s".format(h12, m % 60, if (m / 60 < 12) "AM" else "PM")
        val ms = ModelStore.status(this)
        findViewById<TextView>(R.id.txtModel).text = ms.detail
        findViewById<Button>(R.id.btnModel).apply {
            text = when (ms.state) {
                ModelStore.State.MISSING -> "Download model (~1 GB)"
                ModelStore.State.DOWNLOADING -> "Downloading..."
                ModelStore.State.READY -> "Remove model"
            }
            isEnabled = ms.state != ModelStore.State.DOWNLOADING
        }
        if (ms.state == ModelStore.State.DOWNLOADING && !pollScheduled) {
            pollScheduled = true
            list.postDelayed({ pollScheduled = false; if (!isFinishing) render() }, 3000)
        }
        val waiting = Proposals.all(this).size
        findViewById<Button>(R.id.btnReview).text = if (waiting > 0) "Review events ($waiting)" else "Review events"
        findViewById<TextView>(R.id.txtScan).text = Prefs.lastScan(this).ifEmpty { "No images read yet." }
        findViewById<TextView>(R.id.hint).text =
            "How it works: take any screenshot -> Sort screenshot? appears -> pick a folder (+ New folder anytime). Pick Calendar for anything dated."
    }
    private fun ensurePermsAndStart(auto: Boolean = false) {
        val need = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.READ_MEDIA_IMAGES)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (need.isNotEmpty()) { ActivityCompat.requestPermissions(this, need.toTypedArray(), 1); if (auto) return; }
        try { ContextCompat.startForegroundService(this, Intent(this, ScreenshotService::class.java)); Prefs.setWatching(this, true); if (!auto) toast("ShotSort watching") }
        catch (e: Exception) { toast("Could not start: ${e.message}") }
    }
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
