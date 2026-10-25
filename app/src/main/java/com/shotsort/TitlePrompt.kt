package com.shotsort

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Builds the prompt for the small on-phone language model and checks what it answers.
 * The model only ever suggests a title and a place; the date and time still come from ML Kit,
 * and every suggestion is checked against the screenshot text before it is used.
 * Plain Kotlin on purpose, so it can be unit-tested without a phone.
 */
object TitlePrompt {
    const val PREFILL = "{\"title\": \""
    private const val MAX_CONTEXT = 1000

    class Result(val title: String, val location: String)

    /** The part of the screenshot text around the event, so long chats do not overflow the model. */
    fun window(text: String, offset: Int): String {
        if (text.length <= MAX_CONTEXT) return text.trim()
        val end = minOf(text.length, offset.coerceIn(0, text.length) + MAX_CONTEXT / 2)
        val start = maxOf(0, end - MAX_CONTEXT)
        return text.substring(start, end).trim()
    }

    fun whenText(start: Long, allDay: Boolean): String =
        SimpleDateFormat(if (allDay) "EEE d MMM yyyy" else "EEE d MMM yyyy, h:mm a", Locale.ENGLISH).format(start)

    /** ChatML format, which is what Qwen models expect. The answer is started for the model so it stays in JSON. */
    fun build(context: String, whenText: String): String {
        val system = "You name calendar events. Reply with one line of JSON and nothing else. " +
            "Example: text \"Dr. Mehta dental checkup Sat 14 Nov 4pm, Apollo Clinic MG Road\" " +
            "gives {\"title\": \"Dental checkup with Dr. Mehta\", \"location\": \"Apollo Clinic MG Road\"}"
        val user = "Below is text read from a screenshot (it may contain OCR mistakes):\n" +
            "<<<\n$context\n>>>\n" +
            "It mentions an event on: $whenText.\n" +
            "Write a short title for that event: at most 6 words, no date or time, no quotes. " +
            "Also copy the place only if the text names one.\n" +
            "Answer as {\"title\": \"...\", \"location\": \"...\"} using an empty string when there is no place."
        return "<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n$user<|im_end|>\n<|im_start|>assistant\n$PREFILL"
    }

    /** Returns null when the answer is unusable, in which case the original title is kept. */
    fun parse(raw: String, ocrText: String): Result? {
        val full = PREFILL + raw
        val title = tidy(field(full, "title") ?: return null)
        if (!validTitle(title, ocrText)) return null
        val loc = tidy(field(full, "location") ?: "")
        val place = if (loc.isNotEmpty() && loc.length <= 100 && norm(ocrText).contains(norm(loc))) loc else ""
        return Result(title, place)
    }

    private fun field(json: String, name: String): String? {
        val m = Regex("\"$name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json) ?: return null
        return m.groupValues[1].replace("\\\"", "\"").replace("\\n", " ").replace("\\\\", "\\")
    }

    private fun tidy(s: String): String =
        s.replace(Regex("\\s+"), " ").trim().trim('"', '\'', '.', ',', ';', ':', '-', ' ')

    private val generic = setOf(
        "event", "calendar event", "untitled", "meeting", "appointment", "reminder", "title",
        "screenshot", "event title", "new event", "schedule"
    )

    private fun validTitle(t: String, ocr: String): Boolean {
        if (t.length !in 3..70) return false
        if (t.count { it.isLetter() } < 3) return false
        if (t.split(" ").size > 10) return false
        if (t.contains("<|") || t.contains("{") || t.contains("}")) return false
        if (t.lowercase() in generic) return false
        return grounded(t, ocr)
    }

    /** At least one word of the title must really appear in the screenshot text (matched on its first 4 letters). */
    private fun grounded(title: String, ocr: String): Boolean {
        val words = Regex("[\\p{L}]+").findAll(ocr.lowercase()).map { it.value }.toList()
        val prefixes = words.filter { it.length >= 4 }.map { it.take(4) }.toSet()
        val exact = words.toSet()
        return Regex("[\\p{L}]+").findAll(title.lowercase()).map { it.value }.any { w ->
            (w.length >= 4 && w.take(4) in prefixes) || (w.length == 3 && w in exact)
        }
    }

    private fun norm(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
