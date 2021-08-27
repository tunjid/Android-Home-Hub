package com.tunjid.rcswitchcontrol.common

import kotlinx.coroutines.flow.*


data class Mutation<T>(
    val change: T.() -> T
)

object Mutator {
    fun <T> mutate(item: T, mutation: Mutation<T>) = mutation.change(item)
}

fun <Action : Any, State> Flow<Action>.split(
    mutator: Combined<Action>.(Action) -> Flow<Mutation<State>>
): Flow<Mutation<State>> =
    channelFlow<Flow<Mutation<State>>> mutationFlow@{
        val combined = Combined<Action>()
        this@split.collect { item ->
            val flowKey = item.flowKey
            when (val pipe = combined.namesToFlows[flowKey]) {
                null -> {
                    val created = MutableSharedFlow<Action>()
                    combined.namesToFlows[flowKey] = created
                    channel.send(mutator(combined, item))
                }
                else -> {
                    pipe.subscriptionCount.first { it > 0 }
                    pipe.emit(item)
                }
            }
        }
    }
        .flatMapMerge(
            concurrency = Int.MAX_VALUE,
            transform = { it }
        )


data class State(val count: Int)

sealed class Action {
    data class Add(val count: Int) : Action()
    data class Subtract(val count: Int) : Action()
}

fun check() {
    val actions = MutableSharedFlow<Action>()
    actions.split<Action, State> { action ->
        when (action) {
            is Action.Add -> action.flow.map {
                Mutation { copy(count = count + it.count) }
            }
            is Action.Subtract -> action.flow.map {
                Mutation { copy(count = count - action.count) }
            }
        }
    }
}

data class Combined<Action : Any>(
    internal val namesToFlows: MutableMap<String, MutableSharedFlow<Action>> = mutableMapOf()
) : (Action) -> Flow<Action> {
    override fun invoke(action: Action): Flow<Action> =
        namesToFlows.getValue(action.flowKey)

    inline val <reified Subtype : Action> Subtype.flow: Flow<Subtype>
        get() = invoke(this)
            .onStart { emit(this@flow) }
            as Flow<Subtype>
}

private val Any.flowKey get() = this::class.simpleName!!