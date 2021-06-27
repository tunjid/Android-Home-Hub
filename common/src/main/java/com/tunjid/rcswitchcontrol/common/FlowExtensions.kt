package com.tunjid.rcswitchcontrol.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KProperty1

val <T, R> KProperty1<T, R>.asSuspend: suspend (T) -> R get() = { t: T -> invoke(t) }

fun <A, B : Any, R> Flow<A>.withLatestFrom(
    other: Flow<B>,
    transform: suspend (A, B) -> R
): Flow<R> = flow {
    coroutineScope {
        val latestB = AtomicReference<B?>()
        val outerScope = this
        launch {
            try {
                other.collect { latestB.set(it) }
            } catch (e: CancellationException) {
                outerScope.cancel(e) // cancel outer scope on cancellation exception, too
            }
        }
        collect { a: A ->
            latestB.get()?.let { b -> emit(transform(a, b)) }
        }
    }
}

fun <T, R> Flow<T>.takeUntil(notifier: Flow<R>): Flow<T> = channelFlow {
    val outerScope = this

    launch {
        try {
            notifier.take(1).collect()
            close()
        } catch (e: CancellationException) {
            outerScope.cancel(e) // cancel outer scope on cancellation exception, too
        }
    }
    launch {
        try {
            collect { send(it) }
            close()
        } catch (e: CancellationException) {
            outerScope.cancel(e) // cancel outer scope on cancellation exception, too
        }
    }
}
