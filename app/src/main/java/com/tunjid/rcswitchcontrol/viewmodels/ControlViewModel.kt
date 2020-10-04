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
import android.content.res.Resources
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.jakewharton.rx.replayingShare
import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.models.Device
import com.rcswitchcontrol.protocols.models.Payload
import com.rcswitchcontrol.zigbee.models.ZigBeeCommandInfo
import com.rcswitchcontrol.zigbee.models.ZigBeeDevice
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
import com.tunjid.rcswitchcontrol.data.Record
import com.tunjid.rcswitchcontrol.fragments.DevicesFragment
import com.tunjid.rcswitchcontrol.fragments.HostFragment
import com.tunjid.rcswitchcontrol.fragments.RecordFragment
import com.tunjid.rcswitchcontrol.services.ClientNsdService
import com.tunjid.rcswitchcontrol.services.ServerNsdService
import com.tunjid.rcswitchcontrol.utils.Tab
import com.tunjid.rcswitchcontrol.utils.toLiveData
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.Flowables
import io.reactivex.schedulers.Schedulers.single
import java.util.*

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

            state.copy(isNew = isNew, connectionState = connectionState, commandInfo = commandInfo)
                    .reduceCommands(payload)
                    .reduceHistory(record)
                    .reduceDevices(fetchedDevices)
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

    fun dispatchPayload(key: String, payloadReceiver: Payload.() -> Unit) = dispatchPayload(key, { true }, payloadReceiver)

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

    fun pingServer() = dispatchPayload(
            CommsProtocol::class.java.name,
            { state.value?.commands.let { it == null || it.isEmpty() } }
    ) { action = (CommsProtocol.PING) }

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

    private fun dispatchPayload(key: String, predicate: (() -> Boolean), payloadReceiver: Payload.() -> Unit) {
        val payload = Payload(key)
        payloadReceiver.invoke(payload)
        if (predicate.invoke() && isConnected) nsdConnection.boundService?.sendMessage(payload)
                ?: Unit
    }

    private fun Payload.extractRecord(): Record? = response.let {
        if (it == null || it.isBlank()) null
        else Record(key, it, true)
    }

    private fun Payload.extractCommandInfo(): ZigBeeCommandInfo? {
        if (BLERFProtocol::class.java.name == key || SerialRFProtocol::class.java.name == key) return null
        if (extractDevices() != null) return null
        return data?.deserialize(ZigBeeCommandInfo::class)
    }

    private fun Payload.extractDevices(): List<Device>? {
        val serialized = data ?: return null
        val context = getApplication<Application>()

        return when (key) {
            BLERFProtocol::class.java.name, SerialRFProtocol::class.java.name -> when (action) {
                ClientBleService.ACTION_TRANSMITTER,
                context.getString(R.string.blercprotocol_delete_command),
                context.getString(R.string.blercprotocol_rename_command) -> serialized.deserializeList(RfSwitch::class)
                else -> null
            }
            ZigBeeProtocol::class.java.name -> when (action) {
                context.getString(R.string.zigbeeprotocol_saved_devices) -> serialized.deserializeList(ZigBeeDevice::class)
                else -> null
            }
            else -> null
        }
    }

    enum class Page : Tab {

        HOST, HISTORY, DEVICES;

        override fun createFragment(): Fragment = when (this) {
            HOST -> HostFragment.newInstance()
            HISTORY -> RecordFragment.historyInstance()
            DEVICES -> DevicesFragment.newInstance()
        }

        override fun title(res: Resources): CharSequence = when (this) {
            HOST -> res.getString(R.string.host)
            HISTORY -> res.getString(R.string.history)
            DEVICES -> res.getString(R.string.devices)
        }
    }
}

data class ControlState(
        val isNew: Boolean = false,
        val connectionState: String = "",
        val commandInfo: ZigBeeCommandInfo? = null,
        val history: List<Record> = listOf(),
        val commands: Map<String, List<Record>> = mapOf(),
        val devices: List<Device> = listOf()
)

val ControlState.keys get() = commands.keys.sorted().map(::ProtocolKey)

private fun ControlState.reduceDevices(fetched: List<Device>?) = if (fetched != null) copy(
        devices = (fetched + devices)
                .distinctBy(Device::diffId)
                .sortedBy(Device::name)
) else this

private fun ControlState.reduceHistory(record: Record?) = if (record != null) copy(
        history = (history + record)
) else this

private fun ControlState.reduceCommands(payload: Payload) = copy(
        commands = HashMap(commands).apply {
            this[payload.key] = payload.commands.map { Record(payload.key, it, true) }
        }
)

data class ProtocolKey(val name: String) : Tab {
    val title get() = name.split(".").last().toUpperCase(Locale.US).removeSuffix("PROTOCOL")

    override fun title(res: Resources) = title

    override fun createFragment(): Fragment = RecordFragment.commandInstance(this)
}

private val connectionActions = arrayOf(
        ClientNsdService.ACTION_SOCKET_CONNECTED,
        ClientNsdService.ACTION_SOCKET_CONNECTING,
        ClientNsdService.ACTION_SOCKET_DISCONNECTED,
        ClientNsdService.ACTION_START_NSD_DISCOVERY
)

