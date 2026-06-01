package dev.dettmer.simplenotes.models

/** 🆕 v2.7.0 (Folders): UI-Repräsentation eines Ordners (Name + optionale Farbe). */
data class Folder(
    val name: String,
    val color: String? = null   // canonical "#RRGGBB" wie Note.color; null = keine Farbe
)
