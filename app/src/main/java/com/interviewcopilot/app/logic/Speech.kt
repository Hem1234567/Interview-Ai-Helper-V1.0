package com.interviewcopilot.app.logic

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class SpeechEngine(
    private val ctx: Context,
    private val lang: String,
    private val cbPartial: (String) -> Unit,
    private val cbFinal: (String) -> Unit,
    private val cbRms: (Float) -> Unit,
    private val cbError: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var running = false

    private val intent: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 600L)
    }

    private fun firstText(b: Bundle?): String? =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            cbRms(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            if (!running) return
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    running = false
                    cbError("Microphone permission is missing.")
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> restart(700)
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restart(100)
                else -> restart(600)
            }
        }

        override fun onResults(results: Bundle?) {
            val t = firstText(results)
            if (!t.isNullOrBlank()) cbFinal(t.trim())
            if (running) restart(80)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val t = firstText(partialResults)
            if (!t.isNullOrBlank()) cbPartial(t)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            cbError("Speech recognition is not available on this device.")
            return
        }
        running = true
        main.post { begin() }
    }

    fun stop() {
        running = false
        main.post {
            rec?.destroy()
            rec = null
        }
    }

    private fun begin() {
        if (!running) return
        rec?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        r.setRecognitionListener(listener)
        rec = r
        r.startListening(intent)
    }

    private fun restart(delayMs: Long) {
        main.postDelayed({ begin() }, delayMs)
    }
}
