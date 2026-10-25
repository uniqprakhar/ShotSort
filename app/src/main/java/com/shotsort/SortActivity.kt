package com.shotsort

import android.content.ContentValues
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class SortActivity : AppCompatActivity() {
    private var srcUri: Uri? = null
    private var srcName: String = "screenshot.png"

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_sort)
        srcUri = intent.getStringExtra("uri")?.let { Uri.parse(it) }
        srcName = intent.getStringExtra("name")?.takeIf { it.isNotBlank() } ?: "screenshot_${System.currentTimeMillis()}.png"
        findViewById<TextView>(R.id.txtName).text = srcName
        renderChips()
        findViewById<Button>(R.id.btnNew).setOnClickListener {
            val input = findViewById<EditText>(R.id.inputNew).text.toString()
            if (input.isBlank()) { toast("Type a new folder name"); return@setOnClickListener }
            Prefs.add(this, input)
            moveTo(input.trim())
        }
    }
    private fun renderChips() {
        val grid = findViewById<LinearLayout>(R.id.chipList)
        grid.removeAllViews()
        for (f in Prefs.load(this)) {
            val btn = Button(this).apply {
                text = if (f == "Calendar") "\uD83D\uDCC5 $f (auto-calendar)" else "\uD83D\uDCC1 $f"
                textSize = 15f
                setOnClickListener { moveTo(f) }
            }
            grid.addView(btn)
        }
        val skip = Button(this).apply { text = "Leave in Screenshots"; setOnClickListener { finish() } }
        grid.addView(skip)
    }
    private fun moveTo(folder: String) {
        val clean = folder.trim().replace(Regex("[/\\\\]"), "-").take(40).ifEmpty { "Unsorted" }
        try {
            val moved = moveImage(srcUri, srcName, clean)
            if (moved) toast("Saved to Pictures/$clean") else toast("Couldn't move - left in place")
        } catch (e: Exception) { toast("Couldn't move: ${e.message}") }
        finish()
    }
    private fun moveImage(uri: Uri?, displayName: String, folder: String): Boolean {
        val resolver = contentResolver
        val safeName = displayName.ifBlank { "screenshot_${System.currentTimeMillis()}.png" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, safeName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$folder")
        }
        val dest = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        if (uri == null) return true
        resolver.openInputStream(uri)?.use { input ->
            resolver.openOutputStream(dest)?.use { output -> input.copyTo(output) }
                ?: run { resolver.delete(dest, null, null); return false }
        } ?: return false
        try { resolver.delete(uri, null, null) } catch (_: Exception) {}
        try {
            val path = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "$folder/$safeName")
            android.media.MediaScannerConnection.scanFile(this, arrayOf(path.absolutePath), null, null)
        } catch (_: Exception) {}
        return true
    }
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
