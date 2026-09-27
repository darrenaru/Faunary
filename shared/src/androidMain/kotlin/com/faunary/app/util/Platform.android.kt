package com.faunary.app.util

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

actual object Log {
    actual fun w(tag: String, msg: String, tr: Throwable?) {
        android.util.Log.w(tag, msg, tr)
    }
}

actual fun appInForeground(): Flow<Boolean> =
    ProcessLifecycleOwner.get().lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) }
