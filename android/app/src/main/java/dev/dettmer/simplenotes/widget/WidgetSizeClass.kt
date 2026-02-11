package dev.dettmer.simplenotes.widget

/**
 * 🆕 v1.8.0: Size classification for responsive Note Widget layouts
 *
 * Determines which layout variant to use based on widget dimensions.
 * 🆕 v1.8.1: Added NARROW_SCROLL and WIDE_SCROLL for scrollable mid-size widgets
 */
enum class WidgetSizeClass {
    SMALL,         // Nur Titel
    NARROW_MED,    // Schmal, Vorschau (CompactView)
    NARROW_SCROLL, // 🆕 v1.8.1: Schmal, scrollbare Liste (150dp+)
    NARROW_TALL,   // Schmal, voller Inhalt
    WIDE_MED,      // Breit, Vorschau (CompactView)
    WIDE_SCROLL,   // 🆕 v1.8.1: Breit, scrollbare Liste (150dp+)
    WIDE_TALL      // Breit, voller Inhalt
}
