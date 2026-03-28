package dev.dettmer.simplenotes.widget

import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 🆕 v2.2.0: BroadcastReceiver für das New-Note Shortcut Widget.
 *
 * Muss im AndroidManifest.xml registriert werden.
 * Delegiert an NewNoteWidget.
 */
class NewNoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: NewNoteWidget = NewNoteWidget()
}
