package com.tunjid.rcswitchcontrol.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.reflect.KProperty1

val <T, R> KProperty1<T, R>.asSuspend: suspend (T) -> R get() = { t: T -> invoke(t) }

fun <A, B> Flow<A>.mapDistinct(mapper: suspend (A) -> B) = map(mapper).distinctUntilChanged()

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

fun <T, R> Flow<T>.distinctBy(
    keySelector: (T) -> R
): Flow<T> =
    scan(UniquenessStore<T, R>()) { store, item ->
        val key = keySelector(item)
        println("key: $key; isUnique: ${!store.seenKeys.contains(key)}")
        store.copy(
            emittedItem = item,
            seenKeys = store.seenKeys + key,
            isUnique = !store.seenKeys.contains(key)
        )
    }
        .filter { it.isUnique }
        .mapNotNull { it.emittedItem }


private data class UniquenessStore<T, R>(
    val seenKeys: Set<R> = setOf(),
    val emittedItem: T? = null,
    val isUnique: Boolean = false,
)