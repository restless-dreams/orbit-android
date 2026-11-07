package com.hyuchia.orbit.study

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Today, as study counts days (since 1970-01-01, in the phone's time zone:
 * `LocalDate.toEpochDay`), kept current while it's shown: it moves on at
 * midnight, as the phone's date, time or time zone changes, and when Orbit
 * comes back on screen, as after a night asleep, when no midnight wakes it.
 */
@Composable
fun rememberStudyDay(): Long {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val day by produceState(LocalDate.now().toEpochDay(), context, lifecycle) {
        val changes = Channel<Unit>(Channel.CONFLATED)

        val clock = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                changes.trySend(Unit)
            }
        }

        val back = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                changes.trySend(Unit)
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }

        ContextCompat.registerReceiver(context, clock, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        lifecycle.addObserver(back)

        try {
            followDays(ZonedDateTime::now, changes) { value = it }
        } finally {
            lifecycle.removeObserver(back)
            context.unregisterReceiver(clock)
        }
    }

    return day
}

/**
 * Says what day it is `now`, as days since 1970-01-01 in its time zone, then
 * again at each midnight, and whenever `changes` says the date, the time or
 * the time zone may have changed. It goes on until it's cancelled.
 */
internal suspend fun followDays(now: () -> ZonedDateTime, changes: ReceiveChannel<Unit>, day: (Long) -> Unit): Nothing {
    while (true) {
        val time = now()
        day(time.toLocalDate().toEpochDay())
        val midnight = time.toLocalDate().plusDays(1).atStartOfDay(time.zone)
        withTimeoutOrNull(Duration.between(time, midnight).toMillis().coerceAtLeast(1)) { changes.receive() }
    }
}
