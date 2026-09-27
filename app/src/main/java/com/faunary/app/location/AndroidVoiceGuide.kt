package com.faunary.app.location

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** [VoiceGuide] on Android: TextToSpeech, only started while navigating. */
class AndroidVoiceGuide(private val context: Context) : VoiceGuide {
    private var tts: TextToSpeech? = null
    private var ready = false
    /** Said as soon as the engine finishes starting up. */
    private var pending: String? = null

    override fun start() {
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

    override fun speak(text: String) {
        if (text.isBlank()) return
        val engine = tts
        if (engine == null || !ready) {
            pending = text
            return
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "faunary-nav")
    }

    override fun stop() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        pending = null
    }
}
