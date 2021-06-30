package com.tunjid.rcswitchcontrol.common

import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations

fun <T, R> LiveData<T>.map(mapper: (T) -> R) = Transformations.map(this, mapper)

fun <T> LiveData<T>.distinctUntilChanged() = Transformations.distinctUntilChanged(this)

fun <T, R> LiveData<T>.mapDistinct(mapper: (T) -> R): LiveData<R> =
    Transformations.distinctUntilChanged(
        Transformations.map(this, mapper)
    )
