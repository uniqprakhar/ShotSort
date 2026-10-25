package com.shotsort

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.widget.*
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class SortActivity : AppCompatActivity() {
    private var srcUri: Uri? = null
    private var srcName: String = "screenshot.png"
    private var folderName: String = ""
    private var retryDeleteAfterGrant = false

    // Result of the system "allow ShotSort to delete this screenshot?" prompt.
    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == RESULT_OK) {
            if (retryDeleteAfterGrant) {
                try { srcUri?.let { contentResolver.delete(it, null, null) } } catch (_: Exception) {}
            }
            toast("Moved to Pictures/$folderName")
        } else {
            toast("Copied to Pictures/$folderName - original left in Screenshots")
        }
        finish()
    }

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
                text = if (f == "Calendar") "📅 $f (auto-calendar)" else "📁 $f"
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
        folderName = clean
        val uri = srcUri
        if (uri == null) { toast("Screenshot not found"); finish(); return }
        val dest = try {
            copyImage(uri, srcName, clean)
        } catch (e: Exception) {
            toast("Couldn't move: ${e.message}")
            finish()
            return
        }
        // Remember our own copy so the watcher never asks about it again.
        try { Prefs.markHandled(this, ContentUris.parseId(dest)) } catch (_: Exception) {}
        removeOriginal(uri)
    }

    private fun copyImage(src: Uri, displayName: String, folder: String): Uri {
        val resolver = contentResolver
        val safeName = displayName.ifBlank { "screenshot_${System.currentTimeMillis()}.png" }
        val mime = resolver.getType(src) ?: "image/png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, safeName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$folder")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val dest = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("could not create file")
        try {
            val input = resolver.openInputStream(src) ?: throw IllegalStateException("could not read screenshot")
            input.use { i ->
                val output = resolver.openOutputStream(dest) ?: throw IllegalStateException("could not write file")
                output.use { o -> i.copyTo(o) }
            }
            resolver.update(dest, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            try { resolver.delete(dest, null, null) } catch (_: Exception) {}
            throw e
        }
        return dest
    }

    // Android won't let an app silently delete another app's file (the system screenshot),
    // so if a direct delete is refused we ask the user to confirm once via the system dialog.
    private fun removeOriginal(uri: Uri) {
        try {
            contentResolver.delete(uri, null, null)
            toast("Moved to Pictures/$folderName")
            finish()
            return
        } catch (e: SecurityException) {
            val sender = when {
                e is RecoverableSecurityException -> {
                    retryDeleteAfterGrant = true
                    e.userAction.actionIntent.intentSender
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    MediaStore.createDeleteRequest(contentResolver, listOf(uri)).intentSender
                else -> null
            }
            if (sender != null) {
                try {
                    deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    return
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        toast("Copied to Pictures/$folderName - couldn't remove original")
        finish()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
