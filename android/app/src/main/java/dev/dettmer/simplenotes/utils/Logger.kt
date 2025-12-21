package dev.dettmer.simplenotes.utils

import android.util.Log
import dev.dettmer.simplenotes.BuildConfig

/**
 * Logger: Debug logs nur bei DEBUG builds
 * Release builds zeigen nur Errors/Warnings
 */
object Logger {
    
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }
    
    fun v(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.v(tag, message)
        }
    }
    
    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message)
        }
    }
    
    // Errors und Warnings IMMER zeigen (auch in Release)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
    
    fun w(tag: String, message: String) {
        Log.w(tag, message)
    }
}
