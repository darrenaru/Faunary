package com.faunary.app.util

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState

actual object Log {
    actual fun w(tag: String, msg: String, tr: Throwable?) {
        println("W/$tag: $msg" + (tr?.let { "\n${it.stackTraceToString()}" } ?: ""))
    }
}

actual fun appInForeground(): Flow<Boolean> = callbackFlow {
    val center = NSNotificationCenter.defaultCenter
    val queue = NSOperationQueue.mainQueue
    val active = center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, queue) { trySend(true) }
    val background = center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, queue) { trySend(false) }
    trySend(UIApplication.sharedApplication.applicationState != UIApplicationState.UIApplicationStateBackground)
    awaitClose {
        center.removeObserver(active)
        center.removeObserver(background)
    }
}.distinctUntilChanged()
