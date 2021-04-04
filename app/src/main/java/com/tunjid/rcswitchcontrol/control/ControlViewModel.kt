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
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.models.Payload
import com.tunjid.androidx.core.components.services.HardServiceConnection
import com.tunjid.rcswitchcontrol.client.ClientLoad
import com.tunjid.rcswitchcontrol.client.ClientNsdService
import com.tunjid.rcswitchcontrol.client.ClientState
import com.tunjid.rcswitchcontrol.client.Page
import com.tunjid.rcswitchcontrol.client.Status
import com.tunjid.rcswitchcontrol.client.clientState
import com.tunjid.rcswitchcontrol.common.deserialize
import com.tunjid.rcswitchcontrol.common.filterIsInstance
import com.tunjid.rcswitchcontrol.common.toLiveData
import com.tunjid.rcswitchcontrol.di.AppBroadcasts
import com.tunjid.rcswitchcontrol.di.AppContext
import com.tunjid.rcswitchcontrol.di.dagger
import com.tunjid.rcswitchcontrol.models.Broadcast
import com.tunjid.rcswitchcontrol.server.ServerNsdService
import io.reactivex.Flowable
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class ControlViewModel @Inject constructor(
    @AppContext private val context: Context,
    broadcasts: AppBroadcasts
) : ViewModel() {

    private val disposable: CompositeDisposable = CompositeDisposable()
    private val nsdConnection = HardServiceConnection(context, ClientNsdService::class.java) { pingServer() }

    private val selectedDevices = mutableMapOf<String, Device>()

    val pages: List<Page> = mutableListOf(Page.HISTORY, Page.DEVICES).apply {
        if (ServerNsdService.isServer) add(0, Page.HOST)
    }

    val isBound: Boolean
        get() = nsdConnection.boundService != null

    val state: LiveData<ClientState>

    init {
        val connectionStatuses: Flowable<Status> = broadcasts
            .filterIsInstance<Broadcast.ClientNsd.ConnectionStatus>()
            .map(Broadcast.ClientNsd.ConnectionStatus::status)
            .startWith(Status.Disconnected())

        val serverResponses: Flowable<Payload> = broadcasts
            .filterIsInstance<Broadcast.ClientNsd.ServerResponse>()
            .map(Broadcast.ClientNsd.ServerResponse::data)
            .filter(String::isNotBlank)
            .map(String::deserialize)

        val stateObservable = clientState(
            connectionStatuses,
            serverResponses,
        )
            .doOnSubscribe { nsdConnection.boundService?.onAppForeGround() }

        state = stateObservable.toLiveData()

        // Keep the connection alive
        disposable.add(stateObservable.subscribe())
    }

    override fun onCleared() {
        disposable.clear()
        nsdConnection.unbindService()
        super.onCleared()
    }

    fun dispatchPayload(payload: Payload) {
        nsdConnection.boundService?.sendMessage(payload)
    }

    fun onBackground() = nsdConnection.boundService?.onAppBackground()

    fun forgetService() {
        // Don't call unbind, when the hosting activity is finished,
        // onDestroy will be called and the connection unbound
        nsdConnection.boundService?.stopSelf()

        ClientNsdService.lastConnectedService = null
    }

    fun select(device: Device): Boolean {
        val contains = selectedDevices.keys.contains(device.diffId)
        if (contains) selectedDevices.remove(device.diffId) else selectedDevices[device.diffId] = device
        return !contains
    }

    fun numSelections() = selectedDevices.size

    fun clearSelections() = selectedDevices.clear()

    fun <T> withSelectedDevices(function: (Set<Device>) -> T): T = function.invoke(selectedDevices.values.toSet())

    fun pingServer() = dispatchPayload(Payload(
        key = CommsProtocol.key,
        action = CommsProtocol.pingAction
    ))

    fun load(load: ClientLoad) = when (load) {
        is ClientLoad.NewClient -> {
            nsdConnection.start {
                putExtra(ClientNsdService.NSD_SERVICE_INFO_KEY, load.info)
            }
            nsdConnection.bind {
                putExtra(ClientNsdService.NSD_SERVICE_INFO_KEY, load.info)
            }
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
}
