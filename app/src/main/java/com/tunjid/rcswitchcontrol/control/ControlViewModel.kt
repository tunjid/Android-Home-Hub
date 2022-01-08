/*
 * MIT License
 *
 * Copyright (c) 2019 Adetunji Dahunsi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tunjid.rcswitchcontrol.control

import android.content.Context
import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.models.Payload
import com.tunjid.androidx.core.components.services.HardServiceConnection
import com.tunjid.mutator.Mutation
import com.tunjid.mutator.StateHolder
import com.tunjid.mutator.scopedStateHolder
import com.tunjid.mutator.toMutationStream
import com.tunjid.rcswitchcontrol.client.ClientLoad
import com.tunjid.rcswitchcontrol.client.ClientNsdService
import com.tunjid.rcswitchcontrol.client.State
import com.tunjid.rcswitchcontrol.client.Status
import com.tunjid.rcswitchcontrol.client.clientState
import com.tunjid.rcswitchcontrol.common.ClosableStateHolder
import com.tunjid.rcswitchcontrol.common.asSuspend
import com.tunjid.rcswitchcontrol.common.deserialize
import com.tunjid.rcswitchcontrol.di.AppBroadcasts
import com.tunjid.rcswitchcontrol.di.AppContext
import com.tunjid.rcswitchcontrol.di.UiScope
import com.tunjid.rcswitchcontrol.di.dagger
import com.tunjid.rcswitchcontrol.models.Broadcast
import com.tunjid.rcswitchcontrol.server.ServerNsdService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import javax.inject.Inject

class ControlViewModel @Inject constructor(
    @UiScope scope: CoroutineScope,
    @AppContext private val context: Context,
    broadcasts: @JvmSuppressWildcards AppBroadcasts
) : ClosableStateHolder<Input, ControlState>(scope),
    StateHolder<Input, ControlState> by controlStateHolder(scope, context, broadcasts) {


    init {
        // Keep the connection alive
        state.launchIn(scope)
    }
}

private fun controlStateHolder(
    scope: CoroutineScope,
    context: Context,
    broadcasts: AppBroadcasts
): StateHolder<Input, ControlState> = object : StateHolder<Input, ControlState> {

    val nsdConnection = HardServiceConnection(context, ClientNsdService::class.java) {
        accept(Input.Async.ClientServiceBound(it.state))
    }

    val delegate = scopedStateHolder<Input, ControlState>(
        scope = scope,
        initialState = ControlState(),
        transform = { inputFlow ->
            inputFlow.toMutationStream {
                when (val type = type()) {
                    is Input.Async.ClientServiceBound -> {
                        val connectionStatuses: Flow<Status> = merge(
                            broadcasts
                                .filterIsInstance<Broadcast.ClientNsd.ConnectionStatus>()
                                .map(Broadcast.ClientNsd.ConnectionStatus::status.asSuspend),
                            type.flow
                                .distinctUntilChanged()
                                .flatMapLatest(Input.Async.ClientServiceBound::clientServiceState.asSuspend)
                                .map(State::status.asSuspend)
                        )
                            .onStart { emit(Status.Disconnected()) }

                        val serverResponses: Flow<Payload> = broadcasts
                            .filterIsInstance<Broadcast.ClientNsd.ServerResponse>()
                            .map(Broadcast.ClientNsd.ServerResponse::data.asSuspend)
                            .filter(String::isNotBlank)
                            .map { it.deserialize<Payload>() }

                        scope.clientState(
                            connectionStatuses,
                            serverResponses,
                        )
                            .map { Mutation<ControlState> { copy(clientState = it) } }
                            .onStart { nsdConnection.boundService?.onAppForeGround() }
                    }
                    Input.Async.ForgetServer -> type.flow
                        .distinctUntilChanged()
                        .onEach {
                            // Don't call unbind, when the hosting activity is finished,
                            // onDestroy will be called and the connection unbound
                            nsdConnection.boundService?.stopSelf()

                            ClientNsdService.lastConnectedService = null
                        }
                        .map { Mutation { this } }
                    is Input.Async.Load -> type.flow
                        .distinctUntilChanged()
                        .onEach { action ->
                            when (val load = action.load) {
                                is ClientLoad.NewClient -> {
                                    nsdConnection.start { nsdServiceInfo = load.info }
                                    nsdConnection.bind { nsdServiceInfo = load.info }
                                    Unit
                                }
                                is ClientLoad.ExistingClient -> {
                                    nsdConnection.bind()
                                    context.dagger.appComponent.broadcaster(Broadcast.ClientNsd.StartDiscovery())
                                }
                                ClientLoad.StartServer -> {
                                    nsdConnection.bind()
                                    HardServiceConnection(
                                        context,
                                        ServerNsdService::class.java
                                    ).start()
                                    context.dagger.appComponent.broadcaster(Broadcast.ClientNsd.StartDiscovery())
                                }
                            }
                        }
                        .map {
                            Mutation { this }
                        }
                    Input.Async.PingServer -> type.flow
                        .onEach {
                            accept(
                                Input.Async.ServerCommand(
                                    Payload(
                                        key = CommsProtocol.key,
                                        action = CommsProtocol.pingAction
                                    )
                                )
                            )
                        }
                        .map { Mutation { this } }
                    is Input.Async.ServerCommand -> type.flow
                        .onEach { action -> nsdConnection.boundService?.sendMessage(action.payload) }
                        .map { Mutation { this } }
                    Input.Sync.ClearSelections -> type.flow
                        .map {
                            Mutation {
                                copy(selectedDevices = listOf())
                            }
                        }
                    is Input.Sync.Select -> type.flow.map { action ->
                        Mutation {
                            val filtered =
                                selectedDevices.filterNot { it.diffId == action.device.diffId }
                            copy(
                                selectedDevices = when (filtered.size != selectedDevices.size) {
                                    true -> filtered
                                    false -> selectedDevices + action.device
                                }
                            )
                        }
                    }
                    Input.Async.AppBackgrounded -> type.flow
                        .onEach { nsdConnection.boundService?.onAppBackground() }
                        .map { Mutation { this } }
                }
            }
                .flatMapConcat { listOf(it, updateSelections).asFlow() }
                .onCompletion { nsdConnection.unbindService() }
        }
    )

    override val accept: (Input) -> Unit = delegate.accept

    override val state: StateFlow<ControlState> = delegate.state
}

private val updateSelections: Mutation<ControlState>
    get() = Mutation {
        val state = this
        val selectedIds = state.selectedDevices.map(Device::diffId)
        copy(clientState = state.clientState.copy(devices = state.clientState.devices.map {
            when (it) {
                is Device.RF -> it.copy(isSelected = selectedIds.contains(it.diffId))
                is Device.ZigBee -> it.copy(isSelected = selectedIds.contains(it.diffId))

            }
        }))
    }