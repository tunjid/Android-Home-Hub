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
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelStoreOwner
import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.models.Payload
import com.tunjid.androidx.core.components.services.HardServiceConnection
import com.tunjid.rcswitchcontrol.arch.UiStateMachine
import com.tunjid.rcswitchcontrol.client.ClientLoad
import com.tunjid.rcswitchcontrol.client.ClientNsdService
import com.tunjid.rcswitchcontrol.client.State
import com.tunjid.rcswitchcontrol.client.Status
import com.tunjid.rcswitchcontrol.client.clientState
import com.tunjid.rcswitchcontrol.client.nsdServiceInfo
import com.tunjid.rcswitchcontrol.common.Mutation
import com.tunjid.rcswitchcontrol.common.Mutator
import com.tunjid.rcswitchcontrol.common.asSuspend
import com.tunjid.rcswitchcontrol.common.deserialize
import com.tunjid.rcswitchcontrol.di.AppBroadcasts
import com.tunjid.rcswitchcontrol.di.AppContext
import com.tunjid.rcswitchcontrol.di.UiScope
import com.tunjid.rcswitchcontrol.di.dagger
import com.tunjid.rcswitchcontrol.models.Broadcast
import com.tunjid.rcswitchcontrol.server.ServerNsdService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

interface RootController : ViewModelStoreOwner

val Fragment.rootController: ViewModelStoreOwner
    get() = generateSequence(this, Fragment::getParentFragment)
        .filterIsInstance<RootController>()
        .firstOrNull() ?: this

class ControlViewModel @Inject constructor(
    @UiScope scope: CoroutineScope,
    @AppContext private val context: Context,
    broadcasts: @JvmSuppressWildcards AppBroadcasts
) : UiStateMachine<Input, ControlState>(scope) {

    private val actions = MutableSharedFlow<Input>(
        replay = 1,
        extraBufferCapacity = 5,
    )

    private val nsdConnection = HardServiceConnection(context, ClientNsdService::class.java) {
        accept(Input.Async.ClientServiceBound(it.state))
    }

    val pages: List<Page> = mutableListOf(Page.HISTORY, Page.DEVICES).apply {
        if (ServerNsdService.isServer) add(0, Page.HOST)
    }

    val isBound: Boolean
        get() = nsdConnection.boundService != null

    override val state: StateFlow<ControlState>

    init {
        val connectionStatuses: Flow<Status> = merge(
            broadcasts
                .filterIsInstance<Broadcast.ClientNsd.ConnectionStatus>()
                .map(Broadcast.ClientNsd.ConnectionStatus::status.asSuspend),
            actions.filterIsInstance<Input.Async.ClientServiceBound>()
                .flatMapLatest(Input.Async.ClientServiceBound::clientServiceState.asSuspend)
                .map(State::status.asSuspend)
        )
            .onStart { emit(Status.Disconnected()) }

        val serverResponses: Flow<Payload> = broadcasts
            .filterIsInstance<Broadcast.ClientNsd.ServerResponse>()
            .map(Broadcast.ClientNsd.ServerResponse::data.asSuspend)
            .filter(String::isNotBlank)
            .map { it.deserialize<Payload>() }

        val clientStateObservable = scope.clientState(
            connectionStatuses,
            serverResponses,
        )
            .map { Mutation<ControlState> { copy(clientState = it) } }
            .onStart { nsdConnection.boundService?.onAppForeGround() }

        val actionMutations = actions
            .filterIsInstance<Input.Sync>()
            .map(::onSyncInput)

        state = merge(
            clientStateObservable,
            actionMutations,
        )
            .scan(ControlState(), Mutator::mutate)
            .map { updateSelections(it) }
            .stateIn(
                scope = scope,
                initialValue = ControlState(),
                started = SharingStarted.WhileSubscribed(),
            )

        // Keep the connection alive
        state.launchIn(scope)

        actions
            .filterIsInstance<Input.Async>()
            .map(::onAsyncInput)
            .launchIn(scope)
    }

    override fun close() {
        nsdConnection.unbindService()
        scope.cancel()
    }

    override val accept: (Input) -> Unit = { input ->
        actions.tryEmit(input)
    }

    private fun updateSelections(state: ControlState): ControlState {
        val selectedIds = state.selectedDevices.map(Device::diffId)
        return state.copy(clientState = state.clientState.copy(devices = state.clientState.devices.map {
            when (it) {
                is Device.RF -> it.copy(isSelected = selectedIds.contains(it.diffId))
                is Device.ZigBee -> it.copy(isSelected = selectedIds.contains(it.diffId))

            }
        }))
    }

    private fun onSyncInput(action: Input.Sync) = when (action) {
        is Input.Sync.Select -> Mutation {
            val filtered = selectedDevices.filterNot { it.diffId == action.device.diffId }
            copy(
                selectedDevices = when (filtered.size != selectedDevices.size) {
                    true -> filtered
                    false -> selectedDevices + action.device
                }
            )
        }
        Input.Sync.ClearSelections -> Mutation<ControlState> {
            copy(selectedDevices = listOf())
        }
    }

    private fun onAsyncInput(action: Input.Async): Unit = when (action) {
        is Input.Async.Load -> when (val load = action.load) {
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
                HardServiceConnection(context, ServerNsdService::class.java).start()
                context.dagger.appComponent.broadcaster(Broadcast.ClientNsd.StartDiscovery())
            }
        }
        Input.Async.ForgetServer -> {
            // Don't call unbind, when the hosting activity is finished,
            // onDestroy will be called and the connection unbound
            nsdConnection.boundService?.stopSelf()

            ClientNsdService.lastConnectedService = null
        }
        is Input.Async.ServerCommand -> nsdConnection.boundService
            ?.sendMessage(action.payload) ?: Unit
        is Input.Async.ClientServiceBound,
        Input.Async.PingServer -> onAsyncInput(
            Input.Async.ServerCommand(
                Payload(
                    key = CommsProtocol.key,
                    action = CommsProtocol.pingAction
                )
            )
        )
        Input.Async.AppBackgrounded -> nsdConnection.boundService?.onAppBackground() ?: Unit
    }
}
