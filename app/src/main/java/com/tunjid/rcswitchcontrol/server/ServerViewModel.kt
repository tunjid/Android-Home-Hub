package com.tunjid.rcswitchcontrol.server

import android.content.Context
import android.net.nsd.NsdServiceInfo
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.io.ConsoleWriter
import com.tunjid.androidx.communications.nsd.NsdHelper
import com.tunjid.androidx.recyclerview.diff.Diffable
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.client.ClientNsdService
import com.tunjid.rcswitchcontrol.client.ClientStateCache
import com.tunjid.rcswitchcontrol.common.*
import com.tunjid.rcswitchcontrol.di.AppBroadcaster
import com.tunjid.rcswitchcontrol.di.AppBroadcasts
import com.tunjid.rcswitchcontrol.di.AppContext
import com.tunjid.rcswitchcontrol.models.Broadcast
import com.tunjid.rcswitchcontrol.protocols.ProxyProtocol
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.network.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import javax.inject.Inject

enum class Status {
    Initialized, Registered, Error, Stopped
}

sealed class Input {
    object Restart : Input()
}

data class HostItem(
    val id: Int,
    val text: CharSequence
) : Diffable {
    override val diffId: String get() = id.toString()
}

data class State(
    val serviceName: String = "",
    val numClients: Int = 0,
    val numWrites: Long = 0L,
    val status: Status = Status.Initialized
)

fun Context.items(state: State) = listOf(
    HostItem(R.string.server_info, getString(R.string.server_info, state.serviceName, state.numClients)),
    HostItem(R.string.rename_server, getString(R.string.rename_server)),
    HostItem(R.string.restart_server, getString(R.string.restart_server)),
    HostItem(R.string.stop_server, getString(R.string.stop_server))
)

private data class Response(val data: String) : Input()

private sealed class Output {
    sealed class Server(val status: Status) : Output() {
        data class Initialized(val helper: NsdHelper) : Server(Status.Initialized)
        data class Registered(val service: NsdServiceInfo, val socket: ServerSocket) : Server(Status.Registered)
        data class Error(val service: NsdServiceInfo, val reason: Int) : Server(Status.Error)
    }

    sealed class Client : Output() {
        sealed class Status : Client() {
            data class Added(val port: Int, val writer: WritableSocketConnection) : Status()
            data class Dropped(val port: Int, val writer: WritableSocketConnection) : Status()
        }

        data class Request(val port: Int, val data: String) : Client()
    }
}

private class CachingProtocol(
    protocol: CommsProtocol,
    val cache: ClientStateCache = ClientStateCache()
) : CommsProtocol by protocol

class ServerViewModel @Inject constructor(
    @AppContext context: Context,
    broadcaster: AppBroadcaster,
    broadcasts: @JvmSuppressWildcards AppBroadcasts
) : ViewModel() {

    val state: LiveData<State>

    private val inputs = MutableSharedFlow<Input>(
        replay = 1,
        extraBufferCapacity = 5,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val protocol = CachingProtocol(ProxyProtocol(
        context = context,
        printWriter = ConsoleWriter { inputs.tryEmit(Response(it)) }
    ))

    init {
        val serverOutputs: Flow<Output.Server> = inputs
            .filterIsInstance<Input.Restart>()
            .onStart { emit(Input.Restart) }
            .flatMapLatest { context.registerServer(ServerNsdService.serviceName) }
            .shareIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(), replay = 1)
            .takeUntil(broadcasts.filterIsInstance<Broadcast.ServerNsd.Stop>())

        val registrations: Flow<Output.Server.Registered> = serverOutputs
            .filterIsInstance<Output.Server.Registered>()
            .shareIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(), replay = 1)

        val clientOutputs: Flow<Output.Client> = registrations
            .map(Output.Server.Registered::socket.asSuspend)
            .flatMapLatest(ServerSocket::clients)
            .flatMapMerge(concurrency = Int.MAX_VALUE, transform = protocol::outputs)
            .flowOn(Dispatchers.IO)
            .shareIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(), replay = 1)

        val clients: Flow<Set<WritableSocketConnection>> = clientOutputs
            .filterIsInstance<Output.Client.Status>()
            .scan(setOf<WritableSocketConnection>()) { set, status ->
                when (status) {
                    is Output.Client.Status.Added -> set + status.writer
                    is Output.Client.Status.Dropped -> set - status.writer
                }
            }
            .shareIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(), replay = 1)

        val writes: Flow<String> = merge(
            clientOutputs
                .filterIsInstance<Output.Client.Request>()
                .map(Output.Client.Request::data.asSuspend),
            inputs
                .filterIsInstance<Response>()
                .map(Response::data.asSuspend)
        )
            .shareIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(), replay = 1)

        val backingState = combine(
            registrations
                .map(Output.Server.Registered::service.asSuspend)
                .map(NsdServiceInfo::getServiceName),
            clients
                .map(Set<WritableSocketConnection>::size.asSuspend),
            writes
                .scan(0) { oldCount, _ -> oldCount + 1 },
            serverOutputs.map(Output.Server::status.asSuspend)
                .onStart { emit(Status.Stopped) },
            ::State
        )
            .shareIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(), replay = 1)

        state = backingState.asLiveData()

        // Kick off
        writes
            .withLatestFrom(clients, ::Pair)
            .flowOn(Dispatchers.IO)
            .onEach { (data, writers) ->
                protocol.cache.add(data)
                writers.forEach { it.write(data) }
            }
            .flowOn(Dispatchers.IO)
            .launchIn(viewModelScope)

        registrations
            .onEach { registration ->
                ServerNsdService.isServer = true
                ServerNsdService.serviceName = registration.service.serviceName
                ClientNsdService.lastConnectedService = registration.service.serviceName
                broadcaster(Broadcast.ClientNsd.StartDiscovery())
            }
            .launchIn(viewModelScope)
    }

    fun accept(input: Input) {
        inputs.tryEmit(input)
    }
}

private fun Context.registerServer(name: String): Flow<Output.Server> =
    callbackFlow {
        val socketBuilder = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp()
        val serverSocket = socketBuilder.bind()
        val nsdHelper = NsdHelper.getBuilder(this@registerServer)
            .setRegisterSuccessConsumer { service ->
                channel.trySend(Output.Server.Registered(service, serverSocket))
            }
            .setRegisterErrorConsumer { service, error ->
                channel.trySend(Output.Server.Error(service, error))
                channel.close()
            }
            .build()
            .also { nsdHelper ->
                channel.trySend(Output.Server.Initialized(nsdHelper))
                nsdHelper.registerService(serverSocket.localAddress.port, name)
            }

        awaitClose { nsdHelper.tearDown() }
    }

private fun ServerSocket.clients(): Flow<Socket> =
    flow {
        while (!isClosed) emit(accept())
    }
        .onCompletion { if (!isClosed) dispose() }
        .catch { it.printStackTrace() }

private suspend fun CachingProtocol.outputs(socket: Socket): Flow<Output.Client> {
    val connection = readWriteSocketConnection(socket = socket, side = Side.Server)
    val port = connection.port

    // Initiate conversation with client
    connection.write(cache.payload.serialize())
    connection.write(processInput(CommsProtocol.pingAction.value).serialize())

    return flow<Output.Client> {
        while (connection.canRead && connection.canWrite) {
            val input = connection.read()
            val output = processInput(input).serialize()

            emit(Output.Client.Request(port = port, data = output))
            if (input == null || output == "Bye.") connection.dispose()
        }
    }
        .onCompletion { if (connection.isConnected) connection.dispose() }
        .catch { it.printStackTrace() }
        .onStart { emit(Output.Client.Status.Added(port = port, writer = connection)) }
        .onCompletion { emit(Output.Client.Status.Dropped(port = port, writer = connection)) }
}
