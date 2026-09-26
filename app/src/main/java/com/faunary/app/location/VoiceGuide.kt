package com.faunary.app.location

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Spoken navigation prompts in Indonesian; the engine is only started while navigating. */
@Singleton
class VoiceGuide @Inject constructor(@ApplicationContext private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    /** Said as soon as the engine finishes starting up. */
    private var pending: String? = null

    fun start() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            val engine = tts ?: return@TextToSpeech
            if (status == TextToSpeech.SUCCESS) {
                val id = Locale.forLanguageTag("id-ID")
                if (engine.isLanguageAvailable(id) >= TextToSpeech.LANG_AVAILABLE) engine.language = id
                ready = true
                pending?.let { speak(it) }
            }
            pending = null
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        val engine = tts
        if (engine == null || !ready) {
            pending = text
            return
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "faunary-nav")
    }

    fun stop() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        pending = null
    }
}
