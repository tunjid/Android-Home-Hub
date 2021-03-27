package com.tunjid.rcswitchcontrol.common

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.LiveDataReactiveStreams
import androidx.lifecycle.Transformations
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.annotations.BackpressureKind
import io.reactivex.annotations.BackpressureSupport
import io.reactivex.annotations.CheckReturnValue
import io.reactivex.annotations.SchedulerSupport
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.Flowables
import io.reactivex.schedulers.Schedulers
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

fun <T> Flowable<T>.toLiveData(): LiveData<T> = MainThreadLiveData(this)

fun <T> Flowable<T>.debug(tag: String): Flowable<T> =
    doOnSubscribe { Log.i(tag, "Subscribed") }
        .doOnNext { Log.i(tag, "Saw $it") }
        .doOnCancel { Log.i(tag, "Canceled") }
        .doOnTerminate { Log.i(tag, "Terminated") }
        .doOnError { Log.i(tag, "Error", it) }

fun <T, R> LiveData<T>.map(mapper: (T) -> R) = Transformations.map(this, mapper)

fun <T> LiveData<T>.distinctUntilChanged() = Transformations.distinctUntilChanged(this)

inline fun <reified T> Flowable<in T>.filterIsInstance(): Flowable<T> = filter { it is T }.cast(T::class.java)

fun <T, R> LiveData<T>.mapDistinct(mapper: (T) -> R): LiveData<R> =
    Transformations.distinctUntilChanged(
        Transformations.map(this, mapper)
    )

/**
 * [LiveDataReactiveStreams.fromPublisher] uses [LiveData.postValue] internally which swallows
 * emissions if the occur before it can publish them using it's main thread executor.
 *
 * This class takes the reactive type, observes on the main thread, and uses [LiveData.setValue]
 * which does not swallow emissions.
 */
private class MainThreadLiveData<T>(val source: Flowable<T>) : LiveData<T>() {

    val disposables = CompositeDisposable()

    override fun onActive() {
        disposables.clear()
        disposables.add(source.observeOn(AndroidSchedulers.mainThread()).subscribe(this::setValue))
    }

    override fun onInactive() = disposables.clear()
}

fun <T> Flowable<T>.onErrorComplete(): Flowable<T> =
    onErrorResumeNext { throwable: Throwable ->
        Log.i("TEST", "Terminated bc of throwable", throwable)
        Flowable.empty()
    }

private data class Optional<T>(val item: T?)

fun <T> Flowable<T>.asProperty(
    default: T,
    disposableHandler: (Disposable) -> Unit
) = object : ReadOnlyProperty<Any?, T> {

    private var mostRecent: T? = null

    init {
        disposableHandler(this@asProperty.subscribe(::mostRecent::set))
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = mostRecent ?: default
}

@CheckReturnValue
@Suppress("unused")
@SchedulerSupport(SchedulerSupport.NONE)
@BackpressureSupport(BackpressureKind.FULL)
fun <T> Flowables.fromBlockingCallable(blockingCall: () -> T): Flowable<T> =
    Flowable.fromCallable {
        try {
            Optional(blockingCall())
        } catch (e: Exception) {
            Optional(null)
        }
    }
        .filter { it.item != null }
        .map { it.item!! }

fun <T> Flowable<T>.composeOnIo(): Flowable<T> =
    compose { it.subscribeOn(Schedulers.io()).observeOn(Schedulers.io()) }

operator fun <T> Flowable<T>.plus(other: Flowable<T>) = concatWith(other)