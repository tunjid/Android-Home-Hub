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

package com.tunjid.rcswitchcontrol.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.jakewharton.rx.replayingShare
import com.rcswitchcontrol.protocols.CommonDeviceActions
import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.models.Payload
import com.rcswitchcontrol.zigbee.models.ZigBeeAttribute
import com.rcswitchcontrol.zigbee.models.ZigBeeCommandInfo
import com.rcswitchcontrol.zigbee.models.ZigBeeNode
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol
import com.tunjid.androidx.core.components.services.HardServiceConnection
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.a433mhz.models.RfSwitch
import com.tunjid.rcswitchcontrol.a433mhz.protocols.BLERFProtocol
import com.tunjid.rcswitchcontrol.a433mhz.protocols.SerialRFProtocol
import com.tunjid.rcswitchcontrol.a433mhz.services.ClientBleService
import com.tunjid.rcswitchcontrol.common.Broadcaster
import com.tunjid.rcswitchcontrol.common.deserialize
import com.tunjid.rcswitchcontrol.common.deserializeList
import com.tunjid.rcswitchcontrol.common.toLiveData
import com.tunjid.rcswitchcontrol.models.*
import com.tunjid.rcswitchcontrol.services.ClientNsdService
import com.tunjid.rcswitchcontrol.services.ServerNsdService
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.Flowables
import io.reactivex.schedulers.Schedulers.single

class ControlViewModel(app: Application) : AndroidViewModel(app) {

    private val disposable: CompositeDisposable = CompositeDisposable()
    private val nsdConnection = HardServiceConnection(app, ClientNsdService::class.java) { pingServer() }

    private val selectedDevices = mutableMapOf<String, Device>()

    val pages: List<Page> = mutableListOf(Page.HISTORY, Page.DEVICES).apply {
        if (ServerNsdService.isServer) add(0, Page.HOST)
    }

    val isBound: Boolean
        get() = nsdConnection.boundService != null

    val isConnected: Boolean
        get() = nsdConnection.boundService?.isConnected == true

    val state: LiveData<ControlState>

    init {
        val connectionStatuses = Broadcaster.listen(*connectionActions)
                .map { getConnectionText(it.action ?: "") }
                .startWith(getConnectionText(ClientNsdService.ACTION_SOCKET_DISCONNECTED))

        val serverResponses = Broadcaster.listen(ClientNsdService.ACTION_SERVER_RESPONSE)
                .map { it.getStringExtra(ClientNsdService.DATA_SERVER_RESPONSE) }
                .filter(String::isNotBlank)
                .map { it.deserialize(Payload::class) }

        val stateObservable = Flowables.combineLatest(
                serverResponses,
                connectionStatuses
        ).scan(ControlState()) { state, (payload, connectionState) ->
            val key = payload.key
            val isNew = !state.commands.keys.contains(key)
            val record = payload.extractRecord()
            val fetchedDevices = payload.extractDevices()
            val commandInfo = payload.extractCommandInfo()
            val attributes = payload.extractDeviceAttributes()

            state.copy(isNew = isNew, connectionState = connectionState, commandInfo = commandInfo)
                    .reduceCommands(payload)
                    .reduceHistory(record)
                    .reduceDevices(fetchedDevices)
                    .reduceZigBeeAttributes(attributes)
        }
                .subscribeOn(single())
                .doOnSubscribe { nsdConnection.boundService?.onAppForeGround() }
                .replayingShare()

        state = stateObservable.toLiveData()

        nsdConnection.bind()

        // Keep the connection alive
        disposable.add(stateObservable.subscribe())
    }

    override fun onCleared() {
        disposable.clear()
        nsdConnection.unbindService()
        super.onCleared()
    }

    fun dispatchPayload(payload: Payload) {
        if (isConnected) nsdConnection.boundService?.sendMessage(payload)
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

    fun pingServer() {
        if (state.value?.commands.let { it == null || it.isEmpty() }) dispatchPayload(Payload(
                key = CommsProtocol.key,
                action = CommsProtocol.pingAction
        ))
    }

    private fun getConnectionText(newState: String): String {
        val context = getApplication<Application>()
        val boundService = nsdConnection.boundService
        return when (newState) {
            ClientNsdService.ACTION_SOCKET_CONNECTED -> {
                pingServer()
                if (boundService == null) context.getString(R.string.connected)
                else context.getString(R.string.connected_to, boundService.serviceName)
            }
            ClientNsdService.ACTION_SOCKET_CONNECTING,
            ClientNsdService.ACTION_START_NSD_DISCOVERY ->
                if (boundService == null) context.getString(R.string.connecting)
                else context.getString(R.string.connecting_to, boundService.serviceName)

            ClientNsdService.ACTION_SOCKET_DISCONNECTED -> context.getString(R.string.disconnected)
            else -> ""
        }
    }
}

private val connectionActions = arrayOf(
        ClientNsdService.ACTION_SOCKET_CONNECTED,
        ClientNsdService.ACTION_SOCKET_CONNECTING,
        ClientNsdService.ACTION_SOCKET_DISCONNECTED,
        ClientNsdService.ACTION_START_NSD_DISCOVERY
)

private fun Payload.extractRecord(): Record.Response? = response.let {
    if (it == null || it.isBlank()) null
    else Record.Response(key = key, entry = it)
}

private fun Payload.extractCommandInfo(): ZigBeeCommandInfo? =
        if (key == ZigBeeProtocol.key && action == ZigBeeProtocol.commandInfoAction) data?.deserialize(ZigBeeCommandInfo::class)
        else null

private fun Payload.extractDevices(): List<Device>? = when (val serialized = data) {
    null -> null
    else -> when (key) {
        BLERFProtocol.key, SerialRFProtocol.key -> when (action) {
            ClientBleService.transmitterAction,
            CommonDeviceActions.deleteAction,
            CommonDeviceActions.renameAction -> serialized.deserializeList(RfSwitch::class)
                    .map(Device::RF)
            else -> null
        }
        ZigBeeProtocol.key -> when (action) {
            CommonDeviceActions.refreshDevicesAction -> serialized.deserializeList(ZigBeeNode::class)
                    .map(Device::ZigBee)
            else -> null
        }
        else -> null
    }
}

private fun Payload.extractDeviceAttributes(): List<ZigBeeAttribute>? = when (key) {
    ZigBeeProtocol.key -> when (action) {
        in ZigBeeProtocol.attributeCarryingActions -> data?.deserializeList(ZigBeeAttribute::class)
        else -> null
    }
    else -> null
}