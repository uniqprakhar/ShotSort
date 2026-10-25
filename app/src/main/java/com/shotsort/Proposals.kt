package com.shotsort

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** An event found in a screenshot that is waiting for the user's yes/no. */
data class Proposal(
    val id: String,
    val title: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String,
    val notes: String
)

object Proposals {
    private const val FILE = "shotsort_proposals"
    private const val KEY = "items"

    @Synchronized
    fun all(c: Context): MutableList<Proposal> {
        val s = c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null) ?: return mutableListOf()
        return try {
            val a = JSONArray(s)
            MutableList(a.length()) {
                val o = a.getJSONObject(it)
                Proposal(o.getString("id"), o.getString("title"), o.getLong("start"), o.getLong("end"),
                    o.getBoolean("allDay"), o.optString("location"), o.optString("notes"))
            }
        } catch (_: Exception) { mutableListOf() }
    }

    @Synchronized
    private fun save(c: Context, list: List<Proposal>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("title", it.title).put("start", it.start).put("end", it.end)
                .put("allDay", it.allDay).put("location", it.location).put("notes", it.notes))
        }
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply()
    }

    /** Returns false if an identical event (same title and start) is already waiting. */
    @Synchronized
    fun add(c: Context, title: String, start: Long, end: Long, allDay: Boolean, location: String, notes: String): Boolean {
        val l = all(c)
        if (l.any { it.title.equals(title, true) && it.start == start }) return false
        l.add(Proposal(UUID.randomUUID().toString(), title, start, end, allDay, location, notes))
        save(c, l)
        return true
    }

    @Synchronized
    fun remove(c: Context, id: String) { save(c, all(c).filter { it.id != id }) }
}
