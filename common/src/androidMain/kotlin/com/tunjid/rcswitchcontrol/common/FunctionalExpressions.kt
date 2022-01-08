package com.tunjid.rcswitchcontrol.common

import com.tunjid.mutator.Mutation
import com.tunjid.mutator.StateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

fun <State : Any, SubState : Any> StateHolder<Mutation<State>, State>.derived(
    scope: CoroutineScope,
    mapper: (State) -> SubState,
    mutator: (State, SubState) -> State
) = object : StateHolder<Mutation<SubState>, SubState> {
    override val state: StateFlow<SubState> =
        this@derived.state
            .mapDistinct { mapper(it) }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = mapper(this@derived.state.value)
            )

    override val accept: (Mutation<SubState>) -> Unit = { mutation ->
        this@derived.accept(Mutation {
            val currentState = this
            val mapped = mapper(currentState)
            val mutated = mutation.mutate(mapped)
            mutator(currentState, mutated)
        })
    }
}