package dev.dettmer.simplenotes.widget

/**
 * 🆕 v1.8.0: Size classification for responsive Note Widget layouts
 *
 * Determines which layout variant to use based on widget dimensions.
 */
enum class WidgetSizeClass {
    SMALL,        // Nur Titel
    NARROW_MED,   // Schmal, Vorschau
    NARROW_TALL,  // Schmal, voller Inhalt
    WIDE_MED,     // Breit, Vorschau
    WIDE_TALL     // Breit, voller Inhalt
}
