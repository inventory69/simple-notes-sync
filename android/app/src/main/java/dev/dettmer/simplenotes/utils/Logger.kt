package dev.dettmer.simplenotes.utils

import android.content.Context
import android.util.Log
import dev.dettmer.simplenotes.BuildConfig
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Logger: Debug logs nur bei DEBUG builds + File Logging
 * Release builds zeigen nur Errors/Warnings
 */
object Logger {
    
    private const val MAX_LOG_ENTRIES = 500 // Nur letzte 500 Einträge
    
    private var fileLoggingEnabled = false
    private var logFile: File? = null
    private var appContext: Context? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    /**
     * Setzt den File-Logging Status (für UI Toggle)
     */
    fun setFileLoggingEnabled(enabled: Boolean) {
        fileLoggingEnabled = enabled
        if (!enabled) {
            logFile = null
        }
    }
    
    /**
     * Gibt zurück, ob File-Logging aktiviert ist
     */
    fun isFileLoggingEnabled(): Boolean = fileLoggingEnabled
    
    /**
     * Initialisiert den Logger mit App-Context
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }
    
    /**
     * Aktiviert File-Logging für Debugging
     */
    fun enableFileLogging(context: Context) {
        try {
            logFile = File(context.filesDir, "simplenotes_debug.log")
            fileLoggingEnabled = true
            
            // Clear old log
            logFile?.writeText("")
            
            i("Logger", "📝 File logging enabled: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e("Logger", "Failed to enable file logging", e)
        }
    }
    
    /**
     * Deaktiviert File-Logging
     */
    fun disableFileLogging() {
        fileLoggingEnabled = false
        i("Logger", "📝 File logging disabled")
    }
    
    /**
     * Gibt Log-Datei zurück
     */
    fun getLogFile(): File? = logFile
    
    /**
     * Gibt Log-Datei mit Context zurück (für SettingsActivity)
     */
    fun getLogFile(context: Context): File? {
        if (logFile == null && fileLoggingEnabled) {
            logFile = File(context.filesDir, "simplenotes_debug.log")
        }
        return logFile
    }
    
    /**
     * Löscht die Log-Datei
     */
    fun clearLogFile(context: Context): Boolean {
        return try {
            val file = File(context.filesDir, "simplenotes_debug.log")
            if (file.exists()) {
                file.delete()
                logFile = null
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("Logger", "Failed to clear log file", e)
            false
        }
    }
    
    /**
     * Schreibt Log-Eintrag in Datei
     */
    private fun writeToFile(level: String, tag: String, message: String, throwable: Throwable? = null) {
        if (!fileLoggingEnabled) return
        
        // Lazy-init logFile mit appContext
        if (logFile == null && appContext != null) {
            logFile = File(appContext!!.filesDir, "simplenotes_debug.log")
        }
        
        if (logFile == null) return
        
        try {
            val timestamp = dateFormat.format(Date())
            val logEntry = buildString {
                append("$timestamp [$level] $tag: $message\n")
                throwable?.let {
                    append("  Exception: ${it.message}\n")
                    append("  ${it.stackTraceToString()}\n")
                }
            }
            
            // Append to file
            FileWriter(logFile, true).use { writer ->
                writer.write(logEntry)
            }
            
            // Trim file if too large
            trimLogFile()
            
        } catch (e: Exception) {
            Log.e("Logger", "Failed to write to log file", e)
        }
    }
    
    /**
     * Begrenzt Log-Datei auf MAX_LOG_ENTRIES
     */
    private fun trimLogFile() {
        try {
            val lines = logFile?.readLines() ?: return
            if (lines.size > MAX_LOG_ENTRIES) {
                val trimmed = lines.takeLast(MAX_LOG_ENTRIES)
                logFile?.writeText(trimmed.joinToString("\n") + "\n")
            }
        } catch (e: Exception) {
            Log.e("Logger", "Failed to trim log file", e)
        }
    }
    
    fun d(tag: String, message: String) {
        // Logcat nur in DEBUG builds
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
        // File-Logging IMMER (wenn enabled)
        writeToFile("DEBUG", tag, message)
    }
    
    fun v(tag: String, message: String) {
        // Logcat nur in DEBUG builds
        if (BuildConfig.DEBUG) {
            Log.v(tag, message)
        }
        // File-Logging IMMER (wenn enabled)
        writeToFile("VERBOSE", tag, message)
    }
    
    fun i(tag: String, message: String) {
        // INFO logs IMMER zeigen (auch in Release) - wichtige Events
        Log.i(tag, message)
        // File-Logging IMMER (wenn enabled)
        writeToFile("INFO", tag, message)
    }
    
    // Errors und Warnings IMMER zeigen (auch in Release)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
        writeToFile("ERROR", tag, message, throwable)
    }
    
    fun w(tag: String, message: String) {
        Log.w(tag, message)
        writeToFile("WARN", tag, message)
    }
}
