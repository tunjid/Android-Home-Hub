package com.tunjid.rcswitchcontrol.utils

fun guard(action: () -> Unit, onException: (throwable: Throwable) -> Unit) {
    try {
        action.invoke()
    } catch (throwable: Throwable) {
        onException.invoke(throwable)
    }
}