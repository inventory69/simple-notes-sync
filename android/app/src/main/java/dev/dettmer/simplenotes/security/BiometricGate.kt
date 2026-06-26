package dev.dettmer.simplenotes.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.dettmer.simplenotes.R

@Composable
fun AppLockGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity ?: run {
        content()
        return
    }

    val locked by AppLock.locked.collectAsState()
    if (!locked) {
        content()
        return
    }

    var promptVisible by remember { mutableStateOf(false) }

    fun launch() {
        if (promptVisible) return
        promptVisible = true
        showAppLockPrompt(
            activity = activity,
            onSuccess = {
                AppLock.unlock()
                promptVisible = false
            },
            onCancelled = { promptVisible = false },
            // fail-safe: if no auth available at unlock time, disable lock (can't lock user out)
            onUnrecoverable = {
                AppLock.setEnabled(activity, false)
                promptVisible = false
            }
        )
    }
    LaunchedEffect(locked) { if (locked) launch() }
    LockScreen(onUnlockClick = ::launch)
}

fun showAppLockPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onCancelled: () -> Unit,
    onUnrecoverable: () -> Unit
) {
    when (AppLock.canAuthenticate(activity)) {
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
            onUnrecoverable()
            return
        }
    }
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) =
                onSuccess()

            // covers lockout, cancellation, and other terminal errors
            override fun onAuthenticationError(code: Int, msg: CharSequence) = onCancelled()
            // single failed attempt: prompt stays open, no callback needed
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.app_lock_prompt_title))
        .setSubtitle(activity.getString(R.string.app_lock_prompt_subtitle))
        .setAllowedAuthenticators(AppLock.allowedAuthenticators())
        // no setNegativeButtonText — illegal when DEVICE_CREDENTIAL is allowed
        .build()
    prompt.authenticate(info)
}

@Composable
private fun LockScreen(onUnlockClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lock),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.app_lock_locked_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onUnlockClick) {
                    Text(stringResource(R.string.app_lock_unlock))
                }
            }
        }
    }
}
