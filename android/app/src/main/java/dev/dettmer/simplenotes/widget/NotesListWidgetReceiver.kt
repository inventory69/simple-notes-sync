package dev.dettmer.simplenotes.widget

import androidx.glance.appwidget.GlanceAppWidgetReceiver

class NotesListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: NotesListWidget = NotesListWidget()
}
