package dev.dettmer.simplenotes.widget

import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 🆕 v1.8.0: BroadcastReceiver für das Notiz-Widget
 *
 * Muss im AndroidManifest.xml registriert werden.
 * Delegiert alle Widget-Updates an NoteWidget.
 */
class NoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: NoteWidget = NoteWidget()
}
