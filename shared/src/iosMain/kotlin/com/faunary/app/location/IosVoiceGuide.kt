package com.faunary.app.location

import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance

/** [VoiceGuide] on iOS: AVSpeechSynthesizer with the Indonesian voice. */
class IosVoiceGuide : VoiceGuide {
    private var synthesizer: AVSpeechSynthesizer? = null

    override fun start() {
        if (synthesizer == null) synthesizer = AVSpeechSynthesizer()
    }

    override fun speak(text: String) {
        if (text.isBlank()) return
        val engine = synthesizer ?: AVSpeechSynthesizer().also { synthesizer = it }
        engine.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        engine.speakUtterance(AVSpeechUtterance(string = text).apply { voice = AVSpeechSynthesisVoice.voiceWithLanguage("id-ID") })
    }

    override fun stop() {
        synthesizer?.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        synthesizer = null
    }
}
