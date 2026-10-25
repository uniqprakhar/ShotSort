package com.shotsort

import android.content.Context
import org.json.JSONArray

object Prefs {
    private const val FILE = "shotsort"
    private const val KEY = "folders"
    private const val HANDLED = "handled"
    private const val PROCESSED = "processed"
    private const val WATCHING = "watching"
    private const val CAL_SINCE = "calendarSince"
    private const val DIGEST_MIN = "digestMinutes"
    private const val LAST_SCAN = "lastScan"

    /** The folder whose new images are read for events. */
    const val CALENDAR = "Calendar"
    val DEFAULTS = listOf("Calendar", "Work", "Receipts", "Memes", "Personal")

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(c: Context): MutableList<String> {
        val s = sp(c).getString(KEY, null) ?: return DEFAULTS.toMutableList()
        return try {
            val a = JSONArray(s); MutableList(a.length()) { a.getString(it) }
        } catch (_: Exception) { DEFAULTS.toMutableList() }
    }
    fun save(c: Context, folders: List<String>) {
        val a = JSONArray(); folders.forEach { a.put(it) }
        sp(c).edit().putString(KEY, a.toString()).apply()
    }
    fun add(c: Context, name: String): List<String> {
        val clean = name.trim().replace(Regex("[/\\\\]"), "-").take(40)
        if (clean.isEmpty()) return load(c)
        val l = load(c); if (!l.any { it.equals(clean, true) }) { l.add(clean); save(c, l) }
        return l
    }
    fun remove(c: Context, name: String) {
        val l = load(c); l.removeAll { it == name }; save(c, l)
    }

    // ---- small remembered sets of MediaStore ids ----
    private fun idList(c: Context, key: String): MutableList<String> =
        (sp(c).getString(key, "") ?: "").split(",").filter { it.isNotEmpty() }.toMutableList()
    private fun addId(c: Context, key: String, id: Long) {
        val ids = idList(c, key); ids.add(id.toString())
        sp(c).edit().putString(key, ids.takeLast(300).joinToString(",")).apply()
    }
    private fun hasId(c: Context, key: String, id: Long) = idList(c, key).contains(id.toString())

    /** Copies ShotSort made itself, so they are never offered for sorting again. */
    fun markHandled(c: Context, id: Long) = addId(c, HANDLED, id)
    fun isHandled(c: Context, id: Long) = hasId(c, HANDLED, id)

    /** Calendar-folder images that have already been read. */
    fun markProcessed(c: Context, id: Long) = addId(c, PROCESSED, id)
    fun isProcessed(c: Context, id: Long) = hasId(c, PROCESSED, id)
    fun unmarkProcessed(c: Context, id: Long) {
        val ids = idList(c, PROCESSED); ids.remove(id.toString())
        sp(c).edit().putString(PROCESSED, ids.joinToString(",")).apply()
    }

    /** Only images added to the Calendar folder after this moment (epoch seconds) are read, never the old backlog. */
    fun calendarSince(c: Context): Long {
        val s = sp(c)
        if (!s.contains(CAL_SINCE)) s.edit().putLong(CAL_SINCE, System.currentTimeMillis() / 1000).apply()
        return s.getLong(CAL_SINCE, System.currentTimeMillis() / 1000)
    }

    fun setWatching(c: Context, on: Boolean) { sp(c).edit().putBoolean(WATCHING, on).apply() }
    fun isWatching(c: Context): Boolean = sp(c).getBoolean(WATCHING, true)

    /** Minutes after midnight for the daily review notification. Default 9:00 PM. */
    fun digestMinutes(c: Context): Int = sp(c).getInt(DIGEST_MIN, 21 * 60)
    fun setDigestMinutes(c: Context, m: Int) { sp(c).edit().putInt(DIGEST_MIN, m).apply() }

    /** One-line result of the most recent Calendar-folder scan, shown on the home screen. */
    fun setLastScan(c: Context, msg: String) { sp(c).edit().putString(LAST_SCAN, msg).apply() }
    fun lastScan(c: Context): String = sp(c).getString(LAST_SCAN, "") ?: ""
}
