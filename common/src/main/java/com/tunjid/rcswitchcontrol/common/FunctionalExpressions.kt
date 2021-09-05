package com.tunjid.rcswitchcontrol.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


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

fun <Action, State> stateMachineOf(
    scope: CoroutineScope,
    initialState: State,
    started: SharingStarted = SharingStarted.WhileSubscribed(5000),
    transform: (Flow<Action>) -> Flow<Mutation<State>>
): StateMachine<Action, State> {
    val actions = MutableSharedFlow<Action>()
    return object : StateMachine<Action, State> {
        override val state: StateFlow<State> =
            transform(actions)
                .scan(initialState) { state, mutation ->
                    mutation.change(state)
                }
                .onEach {
                    println("STATE OUT: $it")
                }
                .stateIn(
                    scope = scope,
                    started = started,
                    initialValue = initialState,
                )

        override val accept: (Action) -> Unit = { action ->
            scope.launch {
                // Suspend till the downstream is connected
                actions.subscriptionCount.first { it > 0 }
                actions.emit(action)
            }
        }
    }
}

fun <State, SubState> StateMachine<Mutation<State>, State>.derived(
    scope: CoroutineScope,
    mapper: (State) -> SubState,
    mutator: (State, SubState) -> State
) = object : StateMachine<Mutation<SubState>, SubState> {
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
            val mutated = mutation.change(mapped)
            mutator(currentState, mutated)
        })
    }
}