package com.tunjid.rcswitchcontrol.arch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable

interface StateMachine<Action, State> {
    val accept: (Action) -> Unit
    val state: StateFlow<State>
}

abstract class UiStateMachine<Action, State>(
    protected val scope: CoroutineScope
) : StateMachine<Action, State>, Closeable {
    override fun close() = scope.cancel()
}