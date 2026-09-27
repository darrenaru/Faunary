package com.faunary.app.util

import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Warning-level logging; Logcat on Android, the console on iOS. */
expect object Log {
    fun w(tag: String, msg: String, tr: Throwable? = null)
}

/** True while the app is visible; background work pauses when it turns false. */
expect fun appInForeground(): Flow<Boolean>

fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

fun randomUuid(): String = Uuid.random().toString()
