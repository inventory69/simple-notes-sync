package dev.dettmer.simplenotes.security

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.dettmer.simplenotes.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppLock {
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    // monotone clock — immune to wall-clock changes
    private var backgroundedAt = 0L

    fun init(context: Context) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                backgroundedAt = SystemClock.elapsedRealtime()
            }

            override fun onStart(owner: LifecycleOwner) {
                val ctx = context.applicationContext
                if (isEnabled(ctx) &&
                    SystemClock.elapsedRealtime() - backgroundedAt > graceMillis(ctx)
                ) {
                    _locked.value = true
                }
            }
        })
        // cold start: lock immediately if enabled
        if (isEnabled(context)) _locked.value = true
    }

    fun unlock() {
        _locked.value = false
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    private fun prefs(c: Context) =
        c.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(c: Context): Boolean =
        prefs(c).getBoolean(Constants.KEY_APP_LOCK_ENABLED, Constants.DEFAULT_APP_LOCK_ENABLED)

    fun setEnabled(c: Context, on: Boolean) {
        prefs(c).edit { putBoolean(Constants.KEY_APP_LOCK_ENABLED, on) }
        _locked.value = false
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun graceMillis(c: Context): Long =
        prefs(c).getLong(Constants.KEY_APP_LOCK_GRACE_MS, Constants.DEFAULT_APP_LOCK_GRACE_MS)

    fun setGraceMillis(c: Context, ms: Long) =
        prefs(c).edit { putLong(Constants.KEY_APP_LOCK_GRACE_MS, ms) }

    // STRONG+DEVICE_CREDENTIAL only supported from API 30; WEAK below that
    fun allowedAuthenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        }

    fun canAuthenticate(c: Context): Int =
        BiometricManager.from(c).canAuthenticate(allowedAuthenticators())

    @Suppress("DEPRECATION")
    fun applySecureFlag(a: Activity) {
        if (isEnabled(a)) {
            a.window.setFlags(FLAG_SECURE, FLAG_SECURE)
            val ta = a.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground))
            val bg = ta.getColor(0, android.graphics.Color.WHITE)
            ta.recycle()
            // Best-effort for pre-edge-to-edge devices; on modern Android (targetSdk 35+) these
            // are no-ops since enableEdgeToEdge() disables decorFitsSystemWindows.
            a.window.statusBarColor = bg
            a.window.navigationBarColor = bg
            val td = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // TaskDescription has its OWN statusBarColor/navigationBarColor, separate from
                // Window.statusBarColor — this is what actually paints the Recents header strip.
                android.app.ActivityManager.TaskDescription.Builder()
                    .setBackgroundColor(bg)
                    .setStatusBarColor(bg)
                    .setNavigationBarColor(bg)
                    .build()
            } else {
                android.app.ActivityManager.TaskDescription(null, null, bg)
            }
            a.setTaskDescription(td)
        } else {
            a.window.clearFlags(FLAG_SECURE)
        }
    }
}
