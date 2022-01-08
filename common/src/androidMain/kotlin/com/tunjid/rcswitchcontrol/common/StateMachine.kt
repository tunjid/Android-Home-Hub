package com.tunjid.rcswitchcontrol.common

import com.tunjid.mutator.StateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable

abstract class ClosableStateHolder<Action: Any, State: Any>(
    protected val scope: CoroutineScope
) : StateHolder<Action, State>, Closeable {
    override fun close() = scope.cancel()
}