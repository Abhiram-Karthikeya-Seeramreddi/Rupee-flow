package com.rupeeflow.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** What the microphone is doing right now; drives the "Listening…" UI. */
sealed interface VoiceState {
    data object Idle : VoiceState
    data class Listening(val partial: String = "") : VoiceState
}

/**
 * Voice entry that never leaves the app:
 *  1. Asks for the microphone permission (RECORD_AUDIO) the first time.
 *  2. Listens with Android's on-device SpeechRecognizer and shows live text.
 *  3. Hands the final sentence to [onResult].
 * If the phone has no speech service, it falls back to the system voice screen.
 * Every failure becomes a friendly message via [onMessage]; nothing throws.
 */
class VoiceController internal constructor(private val context: Context) {
    var state: VoiceState by mutableStateOf(VoiceState.Idle)
        private set

    internal var onResult: (String) -> Unit = {}
    internal var onMessage: (String, Boolean) -> Unit = { _, _ -> }
    internal var requestPermission: () -> Unit = {}
    internal var launchFallback: (Intent) -> Unit = {}
    private var recognizer: SpeechRecognizer? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Entry point for the mic button. */
    fun start() {
        if (state is VoiceState.Listening) { stop(); return }
        if (!hasPermission()) { requestPermission(); return }
        listen()
    }

    internal fun onPermissionResult(granted: Boolean) {
        if (granted) { listen(); return }
        val activity = context.findActivity()
        val permanentlyDenied = activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
        onMessage(
            if (permanentlyDenied) "Microphone is blocked. Allow it in Settings to use voice."
            else "Microphone permission is needed for voice entry.",
            permanentlyDenied,
        )
    }

    private fun speechIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Say: 250 biryani UPI")
    }

    private fun listen() {
        val available = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
        if (!available) {
            runCatching { launchFallback(speechIntent()) }.onFailure {
                onMessage("Voice isn't available on this phone. Type it instead.", false)
            }
            return
        }
        try {
            recognizer?.destroy()
            val r = SpeechRecognizer.createSpeechRecognizer(context)
            r.setRecognitionListener(listener)
            recognizer = r
            state = VoiceState.Listening()
            r.startListening(speechIntent())
        } catch (e: Exception) {
            state = VoiceState.Idle
            onMessage("Couldn't start the microphone. Try again.", false)
        }
    }

    fun stop() {
        runCatching { recognizer?.stopListening() }
        state = VoiceState.Idle
    }

    fun release() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        state = VoiceState.Idle
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (state is VoiceState.Listening && text.isNotBlank()) state = VoiceState.Listening(text)
        }

        override fun onResults(results: Bundle?) {
            state = VoiceState.Idle
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (text.isNullOrBlank()) onMessage("Didn't catch that. Try again.", false) else onResult(text)
        }

        override fun onError(error: Int) {
            state = VoiceState.Idle
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that. Try again."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for voice entry."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                    "Voice needs internet on this phone. Type it instead."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Microphone is busy. Try again in a moment."
                SpeechRecognizer.ERROR_CLIENT -> return // happens after a manual stop; nothing to say
                else -> "Voice didn't work. Type it instead."
            }
            onMessage(msg, error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        }
    }
}

/**
 * Remembers a [VoiceController] wired to the permission prompt and the fallback voice screen.
 * [onMessage] gets (text, offerSettings).
 */
@Composable
fun rememberVoiceController(onResult: (String) -> Unit, onMessage: (String, Boolean) -> Unit): VoiceController {
    val context = LocalContext.current
    val controller = remember { VoiceController(context) }
    controller.onResult = onResult
    controller.onMessage = onMessage

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        controller.onPermissionResult(granted)
    }
    val fallback = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) controller.onResult(text)
    }
    controller.requestPermission = {
        try { permission.launch(Manifest.permission.RECORD_AUDIO) } catch (e: Exception) {
            controller.onMessage("Couldn't ask for microphone permission. Try again.", false)
        }
    }
    controller.launchFallback = { intent ->
        try { fallback.launch(intent) } catch (e: ActivityNotFoundException) {
            controller.onMessage("Voice isn't available on this phone. Type it instead.", false)
        }
    }
    DisposableEffect(controller) { onDispose { controller.release() } }
    return controller
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun Context.openAppSettings() {
    try {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}
