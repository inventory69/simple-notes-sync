package dev.dettmer.simplenotes.ui.settings.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the pure parsing strategies behind the calendar-parsing experiment screen.
 * `Strategy`/`Preview`/`computePreview` are internal (not private) exactly so this test can call them directly.
 */
class CalendarParsingExperimentScreenTest {
    private val example = "Max Mustermann 0176 12345678 max.mustermann@example.com / Musterstr. 5 / " +
        "Fenster reparieren / Auftraggeber Meier GmbH"

    // ── RAW ──────────────────────────────────────────────────────────────

    @Test
    fun `raw strategy puts whole text in title and leaves other fields empty`() {
        val preview = computePreview(example, Strategy.RAW)
        assertEquals(example, preview.title)
        assertEquals("", preview.location)
        assertEquals("", preview.description)
        assertEquals("", preview.attendees)
    }

    // ── POSITIONAL ───────────────────────────────────────────────────────

    @Test
    fun `positional strategy maps first two segments to title and location`() {
        val preview = computePreview(example, Strategy.POSITIONAL)
        assertEquals("Max Mustermann 0176 12345678 max.mustermann@example.com", preview.title)
        assertEquals("Musterstr. 5", preview.location)
        assertEquals("Fenster reparieren\nAuftraggeber Meier GmbH", preview.description)
    }

    @Test
    fun `positional strategy handles text without slashes`() {
        val preview = computePreview("just a title", Strategy.POSITIONAL)
        assertEquals("just a title", preview.title)
        assertEquals("", preview.location)
        assertEquals("", preview.description)
    }

    @Test
    fun `positional strategy handles blank input`() {
        val preview = computePreview("", Strategy.POSITIONAL)
        assertEquals("", preview.title)
        assertEquals("", preview.location)
        assertEquals("", preview.description)
    }

    // ── PHONE_REGEX ──────────────────────────────────────────────────────

    @Test
    fun `phone regex strategy moves phone number into description`() {
        val preview = computePreview("Max Mustermann 0176 12345678 / Musterstr. 5 / Auftrag", Strategy.PHONE_REGEX)
        assertEquals("Max Mustermann", preview.title)
        assertEquals("Musterstr. 5", preview.location)
        assertEquals("Tel: 0176 12345678\nAuftrag", preview.description)
    }

    @Test
    fun `phone regex strategy leaves description untouched when no phone found`() {
        val preview = computePreview("Max Mustermann / Musterstr. 5 / Auftrag", Strategy.PHONE_REGEX)
        assertEquals("Max Mustermann", preview.title)
        assertEquals("Auftrag", preview.description)
    }

    // ── LABEL_PREFIX ─────────────────────────────────────────────────────

    @Test
    fun `label prefix strategy routes known prefixes case-insensitively`() {
        val preview = computePreview(
            "N:Max Mustermann/T:0176 12345678/A:Musterstr. 5/E:max@example.com",
            Strategy.LABEL_PREFIX
        )
        assertEquals("Max Mustermann", preview.title)
        assertEquals("Musterstr. 5", preview.location)
        assertEquals("Tel: 0176 12345678", preview.description)
        assertEquals("max@example.com", preview.attendees)
    }

    @Test
    fun `label prefix strategy puts unknown or missing prefix into description`() {
        val preview = computePreview("N:Max/X:unknown/no-prefix-here", Strategy.LABEL_PREFIX)
        assertEquals("Max", preview.title)
        assertEquals("X:unknown\nno-prefix-here", preview.description)
    }

    // ── PHONE_EMAIL_REGEX ────────────────────────────────────────────────

    @Test
    fun `phone and email regex strategy extracts both into description and attendees`() {
        val preview = computePreview(example, Strategy.PHONE_EMAIL_REGEX)
        assertEquals("Max Mustermann", preview.title)
        assertEquals("Musterstr. 5", preview.location)
        assertEquals("Tel: 0176 12345678\nFenster reparieren\nAuftraggeber Meier GmbH", preview.description)
        assertEquals("max.mustermann@example.com", preview.attendees)
    }

    @Test
    fun `phone and email regex strategy leaves attendees empty when no email found`() {
        val preview = computePreview("Max Mustermann 0176 12345678 / Musterstr. 5 / Auftrag", Strategy.PHONE_EMAIL_REGEX)
        assertEquals("", preview.attendees)
        assertEquals("Tel: 0176 12345678\nAuftrag", preview.description)
    }
}
