package com.shotsort

import android.content.Context
import org.json.JSONArray

object Prefs {
    private const val FILE = "shotsort"
    private const val KEY = "folders"
    val DEFAULTS = listOf("Calendar", "Work", "Receipts", "Memes", "Personal")

    fun load(c: Context): MutableList<String> {
        val s = c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return DEFAULTS.toMutableList()
        return try {
            val a = JSONArray(s); MutableList(a.length()) { a.getString(it) }
        } catch (_: Exception) { DEFAULTS.toMutableList() }
    }
    fun save(c: Context, folders: List<String>) {
        val a = JSONArray(); folders.forEach { a.put(it) }
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply()
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

    private const val HANDLED = "handled"
    private const val WATCHING = "watching"

    /** Ids of files ShotSort created itself, so they are never offered for sorting again. */
    fun markHandled(c: Context, id: Long) {
        val sp = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val ids = (sp.getString(HANDLED, "") ?: "").split(",").filter { it.isNotEmpty() }.toMutableList()
        ids.add(id.toString())
        sp.edit().putString(HANDLED, ids.takeLast(100).joinToString(",")).apply()
    }
    fun isHandled(c: Context, id: Long): Boolean =
        (c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(HANDLED, "") ?: "").split(",").contains(id.toString())

    fun setWatching(c: Context, on: Boolean) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(WATCHING, on).apply()
    }
    fun isWatching(c: Context): Boolean =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(WATCHING, true)
}
