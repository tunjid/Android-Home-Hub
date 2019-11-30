package com.tunjid.rcswitchcontrol.utils

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable


class LifecycleDisposable(lifecycle:Lifecycle) : LifecycleEventObserver {

    init {
        lifecycle.addObserver(this)
    }

    internal val disposables = CompositeDisposable()

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) = when (event) {
        Lifecycle.Event.ON_CREATE,
        Lifecycle.Event.ON_START,
        Lifecycle.Event.ON_RESUME,
        Lifecycle.Event.ON_ANY -> Unit
        Lifecycle.Event.ON_PAUSE,
        Lifecycle.Event.ON_STOP,
        Lifecycle.Event.ON_DESTROY -> disposables.clear()
    }

}

fun Disposable.guard(disposer: LifecycleDisposable) = disposer.disposables.add(this)
