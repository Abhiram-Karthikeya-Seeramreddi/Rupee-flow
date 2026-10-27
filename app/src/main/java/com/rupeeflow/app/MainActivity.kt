package com.rupeeflow.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.rupeeflow.app.data.ThemeMode
import com.rupeeflow.app.notify.Notifier
import com.rupeeflow.app.ui.MainViewModel
import com.rupeeflow.app.ui.RupeeFlowRoot
import com.rupeeflow.app.ui.theme.RupeeFlowTheme
import kotlinx.coroutines.launch

/**
 * Single activity. Extends FragmentActivity because BiometricPrompt needs it.
 */
class MainActivity : FragmentActivity() {

    private val vm: MainViewModel by viewModels()
    private var pendingAction by mutableStateOf<String?>(null)
    private var locked by mutableStateOf(true)
    private var backgroundedAt = 0L
    private var prompting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) locked = savedInstanceState.getBoolean("locked", true)
        handleIntent(intent)

        // Keep FLAG_SECURE in sync with the privacy setting.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.settings.collect { s ->
                    if (s?.hideInRecents == true) {
                        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }

        setContent {
            val settings by vm.settings.collectAsState()
            RupeeFlowTheme(settings?.theme ?: ThemeMode.SYSTEM, settings?.dynamicColor ?: false) {
                RupeeFlowRoot(
                    vm = vm,
                    pendingAction = pendingAction,
                    onActionConsumed = { pendingAction = null },
                    locked = locked,
                    onUnlock = { authenticate() },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("locked", locked)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val action = intent?.action ?: return
        pendingAction = when (action) {
            Notifier.ACTION_QUICK_ADD -> if (intent.getStringExtra(Notifier.EXTRA_TX_TYPE) == "INCOME") "INCOME" else action
            Notifier.ACTION_VOICE_ADD, Notifier.ACTION_OPEN_BILLS, Notifier.ACTION_OPEN_BUDGETS, Notifier.ACTION_OPEN_INSIGHTS -> action
            else -> null
        }
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Re-lock after a minute in the background.
        if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > 60_000) locked = true
    }

    private fun authenticate() {
        if (prompting) return
        val s = vm.settings.value
        if (s == null || !s.appLock) { locked = false; return }
        prompting = true
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                prompting = false
                locked = false
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                prompting = false
                // If the phone no longer has any screen lock, don't trap the user out of their data.
                if (errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL || errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT) {
                    locked = false
                    vm.updateSettings { it.copy(appLock = false) }
                    vm.toast("App lock turned off: no screen lock is set on this phone.")
                }
            }
            override fun onAuthenticationFailed() {}
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock RupeeFlow")
            .setSubtitle("Use your fingerprint, face or screen lock")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }
}
