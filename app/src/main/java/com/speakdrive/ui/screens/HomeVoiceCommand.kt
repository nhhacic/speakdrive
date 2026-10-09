package com.speakdrive.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/** What the home screen mic is doing. */
sealed interface HomeVoiceUi {
    data object Idle : HomeVoiceUi

    /** Waiting for the learner to speak; [partial] is what has been heard so far. */
    data class Listening(val partial: String = "") : HomeVoiceUi

    /** Nothing usable was heard; shown for a few seconds. */
    data object NotHeard : HomeVoiceUi

    /** The phone has no speech recognizer, or the mic permission was refused. */
    data object Unavailable : HomeVoiceUi
}

/**
 * Listens for one spoken command ("ôn từ vựng", "nghe truyện", "luyện IELTS"…) with the system
 * speech recognizer and hands the text to [onCommand]. Must be used on the main thread.
 */
class HomeVoiceListener(
    private val context: Context,
    private val onState: (HomeVoiceUi) -> Unit,
    private val onCommand: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null

    fun start(languageTag: String) {
        stop()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onState(HomeVoiceUi.Unavailable)
            return
        }
        val speech = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }
            .onFailure { Log.w(TAG, "No speech recognizer", it) }
            .getOrNull() ?: run {
            onState(HomeVoiceUi.Unavailable)
            return
        }
        recognizer = speech
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults.firstResult()
                if (text.isNotBlank()) onState(HomeVoiceUi.Listening(text))
            }

            override fun onResults(results: Bundle?) {
                val text = results.firstResult()
                stop()
                if (text.isBlank()) {
                    onState(HomeVoiceUi.NotHeard)
                } else {
                    onState(HomeVoiceUi.Idle)
                    onCommand(text)
                }
            }

            override fun onError(error: Int) {
                Log.d(TAG, "Speech recognizer error $error")
                stop()
                onState(
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) HomeVoiceUi.Unavailable
                    else HomeVoiceUi.NotHeard
                )
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        onState(HomeVoiceUi.Listening())
        speech.startListening(intent)
    }

    fun stop() {
        recognizer?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
        recognizer = null
    }

    private fun Bundle?.firstResult(): String =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()

    private companion object {
        const val TAG = "HomeVoiceListener"
    }
}

/**
 * The home screen mic: returns the current [HomeVoiceUi] and a toggle that asks for the mic
 * permission when needed, starts listening, or cancels.
 */
@Composable
fun rememberHomeVoiceCommand(onCommand: (String) -> Unit): Pair<HomeVoiceUi, () -> Unit> {
    val context = LocalContext.current
    val isVi = LocalConfiguration.current.locales[0].language == "vi"
    val languageTag = if (isVi) "vi-VN" else "en-US"
    val latestOnCommand by rememberUpdatedState(onCommand)
    var voice by remember { mutableStateOf<HomeVoiceUi>(HomeVoiceUi.Idle) }
    val listener = remember(context) {
        HomeVoiceListener(context, onState = { voice = it }, onCommand = { latestOnCommand(it) })
    }
    DisposableEffect(listener) { onDispose { listener.stop() } }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listener.start(languageTag) else voice = HomeVoiceUi.Unavailable
    }

    // A "not heard" or "unavailable" note fades back to the idle mic.
    LaunchedEffect(voice) {
        if (voice == HomeVoiceUi.NotHeard || voice == HomeVoiceUi.Unavailable) {
            delay(4_000)
            voice = HomeVoiceUi.Idle
        }
    }

    val toggle: () -> Unit = {
        if (voice is HomeVoiceUi.Listening) {
            listener.stop()
            voice = HomeVoiceUi.Idle
        } else {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) listener.start(languageTag) else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    return voice to toggle
}
