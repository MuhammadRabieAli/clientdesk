package com.example.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.ui.isRtlText
import java.util.Locale

/**
 * Lightweight TextToSpeech wrapper that speaks Gemini's confirmation replies
 * in Arabic or English automatically matching the response script.
 */
class VoiceReplySpeaker(context: Context) {

    @Volatile
    private var isReady: Boolean = false
    private var pendingUtterance: String? = null

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            pendingUtterance?.let { queued ->
                pendingUtterance = null
                speak(queued)
            }
        } else {
            Log.w(TAG, "TextToSpeech initialization failed with status=$status")
        }
    }

    fun speak(text: String) {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return
        val engine = tts ?: return
        if (!isReady) {
            pendingUtterance = cleaned
            return
        }
        try {
            val targetLocale = if (cleaned.isRtlText()) Locale("ar") else Locale.US
            val langStatus = engine.setLanguage(targetLocale)
            if (langStatus == TextToSpeech.LANG_MISSING_DATA ||
                langStatus == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                engine.setLanguage(Locale.getDefault())
            }
            engine.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, "clientdesk_voice_reply")
        } catch (e: Exception) {
            Log.w(TAG, "Failed speaking voice reply", e)
        }
    }

    fun stop() {
        pendingUtterance = null
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    fun shutdown() {
        pendingUtterance = null
        isReady = false
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
    }

    companion object {
        private const val TAG = "VoiceReplySpeaker"
    }
}
