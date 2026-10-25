package com.shotsort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitlePromptTest {
    private val ocr = "Dr. Mehta dental checkup\nSat 14 Nov, 4:00 PM\nApollo Clinic, MG Road, Indore"

    @Test fun parsesNormalAnswer() {
        val r = TitlePrompt.parse("Dental checkup with Dr. Mehta\", \"location\": \"Apollo Clinic, MG Road\"", ocr)
        assertNotNull(r)
        assertEquals("Dental checkup with Dr. Mehta", r!!.title)
        assertEquals("Apollo Clinic, MG Road", r.location)
    }

    @Test fun acceptsParaphraseThatSharesAWordStem() {
        val r = TitlePrompt.parse("Dentist visit\", \"location\": \"\"", ocr)
        assertNotNull(r)
        assertEquals("Dentist visit", r!!.title)
        assertEquals("", r.location)
    }

    @Test fun dropsLocationNotInTheText() {
        val r = TitlePrompt.parse("Dental checkup\", \"location\": \"Taj Hotel Mumbai\"", ocr)
        assertNotNull(r)
        assertEquals("", r!!.location)
    }

    @Test fun rejectsTitleNotGroundedInTheText() {
        assertNull(TitlePrompt.parse("Quarterly budget review\", \"location\": \"\"", ocr))
    }

    @Test fun rejectsGenericTitle() {
        assertNull(TitlePrompt.parse("Meeting\", \"location\": \"\"", "Meeting on Friday at 5"))
    }

    @Test fun rejectsTruncatedOrBrokenAnswer() {
        assertNull(TitlePrompt.parse("Dental check", ocr))
        assertNull(TitlePrompt.parse("", ocr))
    }

    @Test fun rejectsOverlongTitle() {
        assertNull(TitlePrompt.parse("Dental checkup " + "x".repeat(80) + "\", \"location\": \"\"", ocr))
    }

    @Test fun promptIsChatMlAndEndsWithThePrefill() {
        val p = TitlePrompt.build("some text", "Sat 14 Nov 2026, 4:00 PM")
        assertTrue(p.startsWith("<|im_start|>system"))
        assertTrue(p.contains("some text"))
        assertTrue(p.contains("Sat 14 Nov 2026, 4:00 PM"))
        assertTrue(p.endsWith("<|im_start|>assistant\n" + TitlePrompt.PREFILL))
    }

    @Test fun windowKeepsShortTextAndCentersLongText() {
        assertEquals("abc", TitlePrompt.window(" abc ", 1))
        val long = "a".repeat(3000) + "EVENT" + "b".repeat(3000)
        val w = TitlePrompt.window(long, 3000)
        assertTrue(w.length <= 1000)
        assertTrue(w.contains("EVENT"))
    }
}
