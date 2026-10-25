package com.shotsort

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.TimeZone

/** Writes an approved event straight into the phone's calendar (normally the Google account's). */
object CalendarWriter {
    class Result(val ok: Boolean, val message: String)

    private const val DAY = 24L * 60 * 60 * 1000

    fun hasPermission(c: Context): Boolean =
        ContextCompat.checkSelfPermission(c, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun add(c: Context, p: Proposal, title: String): Result {
        if (!hasPermission(c)) return Result(false, "Calendar permission is needed")
        val calId = pickCalendar(c) ?: return Result(false, "No writable calendar found on this phone")
        return try {
            val v = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calId)
                put(CalendarContract.Events.TITLE, title)
                if (p.allDay) {
                    val start = utcMidnightOfLocalDay(p.start)
                    put(CalendarContract.Events.DTSTART, start)
                    put(CalendarContract.Events.DTEND, start + DAY)
                    put(CalendarContract.Events.ALL_DAY, 1)
                    put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                } else {
                    put(CalendarContract.Events.DTSTART, p.start)
                    put(CalendarContract.Events.DTEND, p.end)
                    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                }
                if (p.location.isNotBlank()) put(CalendarContract.Events.EVENT_LOCATION, p.location)
                put(CalendarContract.Events.DESCRIPTION, p.notes)
            }
            val uri = c.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v)
                ?: return Result(false, "The calendar refused the event")
            if (!p.allDay) {
                try {
                    val r = ContentValues().apply {
                        put(CalendarContract.Reminders.EVENT_ID, uri.lastPathSegment!!.toLong())
                        put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                        put(CalendarContract.Reminders.MINUTES, 30)
                    }
                    c.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, r)
                } catch (_: Exception) {}
            }
            Result(true, "Added to your calendar")
        } catch (e: Exception) {
            Result(false, "Couldn't add: ${e.message}")
        }
    }

    private fun utcMidnightOfLocalDay(localMidnight: Long): Long {
        val l = Calendar.getInstance().apply { timeInMillis = localMidnight }
        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        u.clear()
        u.set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH))
        return u.timeInMillis
    }

    private class Row(val id: Long, val score: Int)

    /** Prefers the primary calendar of a Google account; any visible, writable calendar will do otherwise. */
    private fun pickCalendar(c: Context): Long? {
        val base = arrayOf(
            CalendarContract.Calendars._ID, CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.VISIBLE
        )
        // "isPrimary" exists on stock Android and Samsung, but fall back gracefully if a phone lacks it.
        val rows = try { readCalendars(c, base + "isPrimary") } catch (_: Exception) { readCalendars(c, base) }
        return rows.maxWithOrNull(compareBy<Row> { it.score }.thenByDescending { -it.id })?.id
    }

    private fun readCalendars(c: Context, proj: Array<String>): List<Row> {
        val out = mutableListOf<Row>()
        c.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, proj, null, null, null)?.use { q ->
            val primaryIdx = q.getColumnIndex("isPrimary")
            while (q.moveToNext()) {
                val access = q.getInt(2)
                val visible = q.getInt(3)
                if (access < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR || visible != 1) continue
                var score = 0
                if (q.getString(1) == "com.google") score += 2
                if (primaryIdx >= 0 && q.getInt(primaryIdx) == 1) score += 1
                out.add(Row(q.getLong(0), score))
            }
        }
        return out
    }
}
