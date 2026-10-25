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
}
