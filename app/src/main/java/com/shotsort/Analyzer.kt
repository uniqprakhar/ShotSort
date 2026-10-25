package com.shotsort

import android.content.Context
import android.net.Uri
import com.google.mlkit.nl.entityextraction.DateTimeEntity
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityAnnotation
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Calendar
import java.util.TimeZone

/**
 * Reads a screenshot on the phone (ML Kit text recognition), finds dates, times and addresses in the
 * text (ML Kit entity extraction) and stores what it finds as pending proposals for the daily review.
 */
object Analyzer {
    private const val HOUR = 60L * 60 * 1000
    private const val DAY = 24 * HOUR

    private class Hit(val ts: Long, val allDay: Boolean, val offset: Int, val str: String)

    fun analyze(ctx: Context, srcId: Long, uri: Uri, refMillis: Long) {
        val app = ctx.applicationContext
        val image = try {
            InputImage.fromFilePath(app, uri)
        } catch (e: Exception) {
            Prefs.setLastScan(app, "Could not open image: ${e.message}")
            return
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                recognizer.close()
                val text = result.text
                if (text.isBlank()) {
                    Prefs.setLastScan(app, "No text found in the image")
                } else {
                    extract(app, srcId, text, refMillis)
                }
            }
            .addOnFailureListener {
                recognizer.close()
                Prefs.setLastScan(app, "Text reading failed: ${it.message}")
            }
    }

    private fun extract(app: Context, srcId: Long, text: String, ref: Long) {
        val extractor = EntityExtraction.getClient(
            EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
        )
        // The small language model is downloaded once (needs internet the first time), then works offline.
        extractor.downloadModelIfNeeded()
            .addOnSuccessListener {
                val params = EntityExtractionParams.Builder(text)
                    .setReferenceTime(ref)
                    .setReferenceTimeZone(TimeZone.getDefault())
                    .setEntityTypesFilter(setOf(Entity.TYPE_DATE_TIME, Entity.TYPE_ADDRESS))
                    .build()
                extractor.annotate(params)
                    .addOnSuccessListener { anns ->
                        try {
                            val n = buildProposals(app, text, anns, ref)
                            Prefs.setLastScan(
                                app,
                                if (n > 0) "Found $n event(s) - waiting for your evening review" else "Text read, but no upcoming date or time found"
                            )
                        } catch (e: Exception) {
                            Prefs.setLastScan(app, "Could not build events: ${e.message}")
                        } finally {
                            extractor.close()
                        }
                    }
                    .addOnFailureListener {
                        extractor.close()
                        Prefs.unmarkProcessed(app, srcId)
                        Prefs.setLastScan(app, "Date finding failed, will retry: ${it.message}")
                    }
            }
            .addOnFailureListener {
                extractor.close()
                Prefs.unmarkProcessed(app, srcId)
                Prefs.setLastScan(app, "Date model not downloaded yet (connect to internet once), will retry")
            }
    }

    private fun buildProposals(app: Context, text: String, anns: List<EntityAnnotation>, ref: Long): Int {
        val now = System.currentTimeMillis()
        var location = ""
        val found = mutableListOf<Hit>()
        for (a in anns) {
            for (e in a.entities) {
                if (e.type == Entity.TYPE_ADDRESS) {
                    if (location.isEmpty()) location = a.annotatedText.replace("\n", " ").trim()
                } else if (e.type == Entity.TYPE_DATE_TIME) {
                    val d = e.asDateTimeEntity() ?: continue
                    val g = d.dateTimeGranularity
                    val timed = g == DateTimeEntity.GRANULARITY_HOUR ||
                        g == DateTimeEntity.GRANULARITY_MINUTE ||
                        g == DateTimeEntity.GRANULARITY_SECOND
                    val day = g == DateTimeEntity.GRANULARITY_DAY
                    if (timed || day) found.add(Hit(d.timestampMillis, day, a.start, a.annotatedText))
                }
            }
        }
        if (found.isEmpty()) return 0

        // "Date: 14 Nov" on one line and "Time: 4 PM" on another come back as two separate finds, and the
        // bare time gets today's date. Put them back together: one future date + time-only finds.
        var work: List<Hit> = found
        val refMid = midnight(ref)
        val dayDates = found.filter { it.allDay }.map { midnight(it.ts) }.distinct()
        val timedHits = found.filter { !it.allDay }
        if (dayDates.size == 1 && timedHits.isNotEmpty() && dayDates[0] > refMid + DAY) {
            val timeOnly = timedHits.filter { midnight(it.ts) <= refMid + DAY }
            if (timeOnly.isNotEmpty()) {
                val merged = timeOnly.map { Hit(dayDates[0] + (it.ts - midnight(it.ts)), false, it.offset, it.str) }
                work = found.filter { !it.allDay && !timeOnly.contains(it) } + merged
            }
        }

        // Ignore things that are already over.
        val today = midnight(now)
        work = work.filter { if (it.allDay) midnight(it.ts) >= today else it.ts + HOUR >= now }
        if (work.isEmpty()) return 0

        val lines = text.split("\n")
        val allStrs = found.map { it.str }
        val notes = "Read from a screenshot by ShotSort:\n" + text.take(800)
        var added = 0

        fun lineOf(offset: Int) = text.substring(0, minOf(offset, text.length)).count { it == '\n' }
        fun add(title: String, start: Long, end: Long, allDay: Boolean) {
            if (Proposals.add(app, title, start, end, allDay, location, notes)) added++
        }

        val byLine = work.groupBy { lineOf(it.offset) }.toSortedMap()
        for ((li, hs) in byLine) {
            val sorted = hs.sortedBy { it.ts }
            val timed = sorted.filter { !it.allDay }
            val days = sorted.filter { it.allDay }
            val title = titleFor(lines, li, sorted, allStrs)
            for (h in days) {
                if (timed.any { midnight(it.ts) == midnight(h.ts) }) continue
                add(title, midnight(h.ts), midnight(h.ts) + DAY, true)
            }
            if (timed.size == 2 && midnight(timed[0].ts) == midnight(timed[1].ts) && timed[1].ts > timed[0].ts) {
                add(title, timed[0].ts, timed[1].ts, false)
            } else {
                for (h in timed) add(title, h.ts, h.ts + HOUR, false)
            }
        }
        return added
    }

    private fun titleFor(lines: List<String>, idx: Int, hits: List<Hit>, allStrs: List<String>): String {
        var line = lines.getOrElse(idx) { "" }
        for (h in hits) line = line.replace(h.str, " ", ignoreCase = true)
        val own = clean(line)
        if (usable(own)) return own.take(80)
        for (l in lines) {
            var t = l
            for (s in allStrs) t = t.replace(s, " ", ignoreCase = true)
            t = clean(t)
            if (usable(t)) return t.take(80)
        }
        return "Event from screenshot"
    }

    private fun clean(s: String): String =
        s.replace(Regex("^[\\s\\-–—:@,|·•]+|[\\s\\-–—:@,|·•]+$"), "").replace(Regex("\\s+"), " ").trim()

    private fun usable(s: String): Boolean =
        s.count { it.isLetter() } >= 4 &&
            !s.matches(Regex("(?i)(date|time|when|on|at|from|to|date & time|date and time)"))

    private fun midnight(ts: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = ts
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
}
