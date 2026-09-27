package com.faunary.app.location

/** Spoken navigation prompts in Indonesian; the engine is only started while navigating. */
interface VoiceGuide {
    fun start()

    /** Replaces whatever is being said. */
    fun speak(text: String)

    fun stop()
}
