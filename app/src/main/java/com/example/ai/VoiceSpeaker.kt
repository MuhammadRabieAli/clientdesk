package com.example.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.ui.isRtlText
import java.util.Locale

/**
 * Bilingual (English + Arabic) Text-to-Speech helper for reading back Voice Assistant replies.
 */
class VoiceSpeaker(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingUtterance: String? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isReady = true
                    val queued = pendingUtterance
                    pendingUtterance = null
                    if (!queued.isNullOrBlank()) {
                        speak(queued)
                    }
                } else {
                    Log.w(TAG, "TextToSpeech init returned status $status")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TextToSpeech unavailable on this device", e)
        }
    }

    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val engine = tts ?: return
        if (!isReady) {
            pendingUtterance = clean
            return
        }
        try {
            val targetLocale = if (clean.isRtlText()) {
                Locale.forLanguageTag("ar")
            } else {
                Locale.US
            }
            val langStatus = engine.isLanguageAvailable(targetLocale)
            if (langStatus >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = targetLocale
            } else {
                engine.language = Locale.getDefault()
            }
            engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "clientdesk_voice_${System.nanoTime()}")
        } catch (e: Exception) {
            Log.w(TAG, "TextToSpeech speak failed", e)
        }
    }

    fun stop() {
        pendingUtterance = null
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    fun release() {
        pendingUtterance = null
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        } finally {
            tts = null
            isReady = false
        }
    }

    private companion object {
        const val TAG = "VoiceSpeaker"
    }
}
