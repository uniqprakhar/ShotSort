package com.shotsort

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File

/**
 * The small open model (Qwen2.5 1.5B Instruct, Apache-2.0, 4-bit GGUF, about 1 GB) that writes event titles.
 * It is optional: until it is on the phone, titles come from the built-in rules.
 */
object ModelStore {
    const val FILE_NAME = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
    const val URL = "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/$FILE_NAME"
    private const val MIN_BYTES = 500_000_000L

    enum class State { MISSING, DOWNLOADING, READY }
    class Status(val state: State, val detail: String)

    /** Same path in every process (the model runs in its own process). */
    fun file(c: Context): File {
        val dir = c.getExternalFilesDir(null) ?: c.filesDir
        return File(dir, "models/$FILE_NAME")
    }

    fun isReady(c: Context): Boolean = file(c).let { it.exists() && it.length() >= MIN_BYTES } && Prefs.modelReady(c)

    fun startDownload(c: Context): Boolean {
        return try {
            val f = file(c)
            f.parentFile?.mkdirs()
            if (f.exists()) f.delete()
            val req = DownloadManager.Request(Uri.parse(URL))
                .setTitle("ShotSort title model")
                .setDescription("Small open AI model, one-time download")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(f))
            val id = c.getSystemService(DownloadManager::class.java).enqueue(req)
            Prefs.setDownloadId(c, id)
            Prefs.setModelReady(c, false)
            Prefs.setModelNote(c, "")
            true
        } catch (e: Exception) {
            Prefs.setModelNote(c, "Could not start download: ${e.message}")
            false
        }
    }

    /** Checks on a running download and returns what the home screen should say. */
    fun status(c: Context): Status {
        val id = Prefs.downloadId(c)
        if (id >= 0) {
            val dm = c.getSystemService(DownloadManager::class.java)
            try {
                dm.query(DownloadManager.Query().setFilterById(id))?.use { q ->
                    if (q.moveToFirst()) {
                        val st = q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val got = q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        when (st) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                Prefs.setDownloadId(c, -1)
                                if (looksLikeGguf(file(c))) {
                                    Prefs.setModelReady(c, true)
                                    Prefs.setModelNote(c, "")
                                } else {
                                    Prefs.setModelNote(c, "Downloaded file was not a valid model")
                                }
                            }
                            DownloadManager.STATUS_FAILED -> {
                                Prefs.setDownloadId(c, -1)
                                Prefs.setModelNote(c, "Download failed (code ${q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))}). Try again on Wi-Fi.")
                            }
                            else -> {
                                val pct = if (total > 0) " ${got * 100 / total}%" else ""
                                return Status(State.DOWNLOADING, "Downloading title model...$pct")
                            }
                        }
                    } else {
                        Prefs.setDownloadId(c, -1)
                    }
                }
            } catch (_: Exception) {}
        }
        if (isReady(c)) return Status(State.READY, "Smart titles are on (open AI model on this phone)")
        val note = Prefs.modelNote(c)
        return Status(State.MISSING, if (note.isNotEmpty()) note else "Optional: download a small open AI model (about 1 GB) for better event titles")
    }

    fun looksLikeGguf(f: File): Boolean = try {
        f.exists() && f.length() >= MIN_BYTES && f.inputStream().use { i ->
            val h = ByteArray(4); i.read(h) == 4 && String(h, Charsets.ISO_8859_1) == "GGUF"
        }
    } catch (_: Exception) { false }

    /** For a model file the user downloaded themselves and picked from storage. */
    fun importFrom(c: Context, uri: Uri): Boolean {
        return try {
            val f = file(c)
            f.parentFile?.mkdirs()
            c.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { out -> input.copyTo(out) } }
                ?: return false
            if (looksLikeGguf(f)) {
                Prefs.setModelReady(c, true); Prefs.setModelNote(c, ""); true
            } else {
                f.delete(); Prefs.setModelNote(c, "That file is not a valid GGUF model"); false
            }
        } catch (e: Exception) {
            Prefs.setModelNote(c, "Could not import: ${e.message}"); false
        }
    }

    fun remove(c: Context) {
        try { file(c).delete() } catch (_: Exception) {}
        Prefs.setModelReady(c, false)
        Prefs.setModelNote(c, "")
    }
}
