package dev.dettmer.simplenotes.utils

/**
 * Exception für Sync-spezifische Fehler
 * 
 * Verwendet anstelle von generischen Exceptions für bessere
 * Fehlerbehandlung und klarere Fehlermeldungen.
 */
class SyncException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Exception für Validierungsfehler
 * 
 * Verwendet für ungültige Eingaben oder Konfigurationsfehler.
 */
class ValidationException(
    message: String
) : IllegalArgumentException(message)
