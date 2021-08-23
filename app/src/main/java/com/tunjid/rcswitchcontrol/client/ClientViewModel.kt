package com.tunjid.rcswitchcontrol.client

import android.net.nsd.NsdServiceInfo
import com.rcswitchcontrol.protocols.models.Payload
import com.tunjid.rcswitchcontrol.arch.UiStateMachine
import com.tunjid.rcswitchcontrol.common.*
import com.tunjid.rcswitchcontrol.di.AppBroadcaster
import com.tunjid.rcswitchcontrol.di.AppBroadcasts
import com.tunjid.rcswitchcontrol.di.UiScope
import com.tunjid.rcswitchcontrol.models.Broadcast
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class State(
    val status: Status = Status.Disconnected(),
    val serviceName: String? = null,
    val inBackground: Boolean = true,
    val isStopped: Boolean = false
)

@kotlinx.serialization.Serializable
sealed class Status: Writable {
    @kotlinx.serialization.Serializable
    data class Connected(val serviceName: String, val epoch: Long = System.currentTimeMillis()) : Status()
    @kotlinx.serialization.Serializable
    data class Connecting(val serviceName: String? = null, val epoch: Long = System.currentTimeMillis()) : Status()
    @kotlinx.serialization.Serializable
    data class Disconnected(val epoch: Long = System.currentTimeMillis()) : Status()
}

sealed class Input {
    data class Connect(val service: NsdServiceInfo) : Input()
    data class Send(val payload: Payload) : Input()
    data class ContextChanged(val inBackground: Boolean) : Input()
}

private sealed class Output {
    sealed class Connection(val status: Status) : Output() {
        data class Connected(val serviceName: String, val writer: WritableSocketConnection) : Connection(status = Status.Connected(serviceName))
        data class Connecting(val serviceName: String) : Connection(status = Status.Connecting(serviceName))
        object Disconnected : Connection(status = Status.Disconnected())
    }

    data class Response(val data: String) : Output()
}

private data class Write(
    val data: String,
    val writer: WritableSocketConnection?
)

private val Write.isValid get() = writer != null && writer.canWrite

private suspend fun Write.print() = writer?.write(data) ?: Unit

class ClientViewModel @Inject constructor(
    @UiScope scope: CoroutineScope,
    broadcaster: AppBroadcaster,
    broadcasts: @JvmSuppressWildcards AppBroadcasts,
) : UiStateMachine<Input, State>(scope) {

    override val state: StateFlow<State>

    private val inputs = MutableSharedFlow<Input>(
        replay = 1,
        extraBufferCapacity = 3,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        val outputs = inputs
            .filterIsInstance<Input.Connect>()
            .map(Input.Connect::service.asSuspend)
            .flatMapConcat(NsdServiceInfo::outputs)
            .shareIn(scope = scope, started = SharingStarted.WhileSubscribed(), replay = 1)

        val connections = outputs
            .filterIsInstance<Output.Connection>()
            .shareIn(scope = scope, started = SharingStarted.WhileSubscribed(), replay = 1)

        state = merge(
            broadcasts
                .filterIsInstance<Broadcast.ClientNsd.Stop>()
                .map { Mutation { copy(isStopped = true) } },
            connections
                .map(Output.Connection::mutation),
            inputs
                .filterIsInstance<Input.ContextChanged>()
                .map(Input.ContextChanged::inBackground.asSuspend)
                .map { Mutation { copy(inBackground = it) } },
        )
            .scan(State(), Mutator::mutate)
            .stateIn(
                scope = scope,
                initialValue = State(),
                started = SharingStarted.WhileSubscribed(),
            )

        inputs
            .filterIsInstance<Input.Send>()
            .map(Input.Send::payload.asSuspend)
            .map(Payload::serialize)
            .withLatestFrom(connections) { data, connection ->
                Write(
                    data = data, writer = when (connection) {
                        is Output.Connection.Connected -> connection.writer
                        is Output.Connection.Connecting,
                        Output.Connection.Disconnected -> null
                    }
                )
            }
            .filter(Write::isValid.asSuspend)
            .onEach(Write::print)
            .flowOn(Dispatchers.IO)
            .launchIn(scope)

        outputs
            .filterIsInstance<Output.Response>()
            .map(Output.Response::data.asSuspend)
            .map(Broadcast.ClientNsd::ServerResponse)
            .onEach { broadcaster(it) }
            .launchIn(scope)

        state
            .map(State::status.asSuspend)
            .distinctUntilChanged()
            .map(Broadcast.ClientNsd::ConnectionStatus)
            .onEach { broadcaster(it) }
            .launchIn(scope)
    }

    override val accept: (Input) -> Unit = { input ->
        inputs.tryEmit(input)
    }
}

private suspend fun NsdServiceInfo.outputs(): Flow<Output> =
    flowOf<Output>(Output.Connection.Connecting(serviceName))
        .onCompletion { failure ->
            if (failure != null) return@onCompletion

            val connection = readWriteSocketConnection(
                socket = aSocket(ActorSelectorManager(Dispatchers.IO))
                    .tcp()
                    .connect(hostname = host.hostName, port = port),
                side = Side.Client
            )

            emitAll(flow<Output> {
                while (connection.canRead && connection.canWrite) {
                    val input = connection.read()
                    if (input == null || input == "Bye.") connection.dispose()
                    if (input != null) emit(Output.Response(data = input))
                }
            }
                .catch { emit(Output.Connection.Disconnected) }
                .onStart { emit(Output.Connection.Connected(serviceName, writer = connection)) }
                .onCompletion {
                    if (connection.isConnected) connection.dispose()
                    emit(Output.Connection.Disconnected)
                })
        }
        .flowOn(Dispatchers.IO)

private fun Output.Connection.mutation(): Mutation<State> =
    Mutation {
        copy(status = this@mutation.status, serviceName = when (this@mutation) {
            is Output.Connection.Connected -> this@mutation.serviceName
            is Output.Connection.Connecting -> this@mutation.serviceName
            Output.Connection.Disconnected -> null
        })
    }
