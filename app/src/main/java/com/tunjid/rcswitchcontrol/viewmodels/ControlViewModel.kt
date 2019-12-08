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
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.DiffUtil.DiffResult
import com.tunjid.androidx.core.components.services.HardServiceConnection
import com.tunjid.androidx.functions.collections.replace
import com.tunjid.androidx.recyclerview.diff.Diff
import com.tunjid.androidx.recyclerview.diff.Differentiable
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster
import com.tunjid.rcswitchcontrol.data.Device
import com.tunjid.rcswitchcontrol.data.Payload
import com.tunjid.rcswitchcontrol.data.Record
import com.tunjid.rcswitchcontrol.data.RfSwitch
import com.tunjid.rcswitchcontrol.data.ZigBeeCommandInfo
import com.tunjid.rcswitchcontrol.data.ZigBeeDevice
import com.rcswitchcontrol.protocols.persistence.Converter.Companion.deserialize
import com.rcswitchcontrol.protocols.persistence.Converter.Companion.deserializeList
import com.tunjid.rcswitchcontrol.nsd.protocols.BLERFProtocol
import com.tunjid.rcswitchcontrol.nsd.protocols.CommsProtocol
import com.tunjid.rcswitchcontrol.nsd.protocols.SerialRFProtocol
import com.tunjid.rcswitchcontrol.nsd.protocols.ZigBeeProtocol
import com.tunjid.rcswitchcontrol.services.ClientBleService
import com.tunjid.rcswitchcontrol.services.ClientNsdService
import com.tunjid.rcswitchcontrol.services.ServerNsdService
import com.tunjid.rcswitchcontrol.utils.toSafeLiveData
import io.reactivex.android.schedulers.AndroidSchedulers.mainThread
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers.single
import java.util.*

class ControlViewModel(app: Application) : AndroidViewModel(app) {

    private val disposable: CompositeDisposable = CompositeDisposable()

    private val stateProcessor: PublishProcessor<State> = PublishProcessor.create()
    private val inPayloadProcessor: PublishProcessor<Payload> = PublishProcessor.create()
    private val outPayloadProcessor: PublishProcessor<Payload> = PublishProcessor.create()
    private val connectionStateProcessor: PublishProcessor<String> = PublishProcessor.create()
    private val nsdConnection = HardServiceConnection(app, ClientNsdService::class.java, this::onServiceConnected)

    private val selectedDevices: MutableSet<Device> = mutableSetOf()
    private val commands: MutableMap<String, MutableList<Record>> = mutableMapOf()
    private val payloadQueue: Queue<Payload> = LinkedList()
    private val history: MutableList<Record> = mutableListOf()
    private val actualKeys: MutableList<ProtocolKey> = mutableListOf()

    val devices: MutableList<Device> = mutableListOf()
    val pages: MutableList<Page> = mutableListOf(Page.HISTORY, Page.DEVICES).apply {
        if (ServerNsdService.isServer) add(0, Page.HOST)
    }

    @Volatile
    var isProcessing = false

    val keys: List<ProtocolKey>
        get() = actualKeys

    val isBound: Boolean
        get() = nsdConnection.boundService != null

    val isConnected: Boolean
        get() = nsdConnection.boundService?.isConnected == true

    init {
        nsdConnection.bind()
        listenForOutputPayloads()
        listenForInputPayloads()
        listenForBroadcasts()
    }

    override fun onCleared() {
        disposable.clear()
        nsdConnection.unbindService()
        super.onCleared()
    }

    fun <T : State> listen(type: Class<T>, predicate: (state: T) -> Boolean = { true }): LiveData<T> = stateProcessor
            .filter(type::isInstance)
            .cast(type)
            .filter(predicate)
            .toSafeLiveData()

    fun dispatchPayload(key: String, payloadReceiver: Payload.() -> Unit) = dispatchPayload(key, { true }, payloadReceiver)

    fun onBackground() = nsdConnection.boundService?.onAppBackground()

    fun getCommands(key: String?) = if (key == null) history else commands.getOrPut(key) { mutableListOf() }

    fun lastIndex(key: String?): Int = getCommands(key).size - 1

    fun forgetService() {
        // Don't call unbind, when the hosting activity is finished,
        // onDestroy will be called and the connection unbound
        nsdConnection.boundService?.stopSelf()

        ClientNsdService.lastConnectedService = null
    }

    fun connectionState(): LiveData<String> = connectionStateProcessor.startWith({
        val boundService = nsdConnection.boundService
        boundService?.onAppForeGround()

        getConnectionText(boundService?.connectionState
                ?: ClientNsdService.ACTION_SOCKET_DISCONNECTED)

    }()).observeOn(mainThread()).toSafeLiveData()

    fun select(device: Device): Boolean {
        val contains = selectedDevices.contains(device)
        if (contains) selectedDevices.remove(device) else selectedDevices.add(device)
        return !contains
    }

    fun numSelections() = selectedDevices.size

    fun clearSelections() = selectedDevices.clear()

    fun <T> withSelectedDevices(function: (Set<Device>) -> T): T = function.invoke(selectedDevices)

    fun pingServer() = dispatchPayload(CommsProtocol::class.java.name, commands::isEmpty) { action = (CommsProtocol.PING) }

    private fun onServiceConnected(service: ClientNsdService) {
        connectionStateProcessor.onNext(getConnectionText(service.connectionState))
        pingServer()
    }

    private fun onIntentReceived(intent: Intent) {
        when (val action = intent.action) {
            ClientNsdService.ACTION_SOCKET_CONNECTED,
            ClientNsdService.ACTION_SOCKET_CONNECTING,
            ClientNsdService.ACTION_SOCKET_DISCONNECTED -> connectionStateProcessor.onNext(getConnectionText(action))
            ClientNsdService.ACTION_START_NSD_DISCOVERY -> connectionStateProcessor.onNext(getConnectionText(ClientNsdService.ACTION_SOCKET_CONNECTING))
            ClientNsdService.ACTION_SERVER_RESPONSE -> intent.getStringExtra(ClientNsdService.DATA_SERVER_RESPONSE)?.apply {
                if (isEmpty()) return@apply
                payloadQueue.add(deserialize(Payload::class))

                if (isProcessing) return@apply
                isProcessing = true
                inPayloadProcessor.onNext(payloadQueue.poll())
            }
        }
    }

    private fun getConnectionText(newState: String): String {
        var text = ""
        val context = getApplication<Application>()
        val boundService = nsdConnection.boundService

        when (newState) {
            ClientNsdService.ACTION_SOCKET_CONNECTED -> {
                pingServer()
                text = if (boundService == null) context.getString(R.string.connected)
                else context.getString(R.string.connected_to, boundService.serviceName)
            }

            ClientNsdService.ACTION_SOCKET_CONNECTING -> text =
                    if (boundService == null) context.getString(R.string.connecting)
                    else context.getString(R.string.connecting_to, boundService.serviceName)

            ClientNsdService.ACTION_SOCKET_DISCONNECTED -> text = context.getString(R.string.disconnected)
        }
        return text
    }

    private fun dispatchPayload(key: String, predicate: (() -> Boolean), payloadReceiver: Payload.() -> Unit) = Payload(key).run {
        payloadReceiver.invoke(this)
        if (predicate.invoke()) outPayloadProcessor.onNext(this)
    }

    private fun diffDevices(fetched: List<Device>): Diff<Device> =
            Diff.calculate(devices, fetched) { current, server ->
                mutableSetOf<Device>().apply {
                    addAll(server)
                    addAll(current)
                }.toList()
            }

    private fun diffHistory(record: Record): Diff<Record> = Diff.calculate(
            history,
            listOf(record),
            { current, responses -> current + responses },
            { response -> Differentiable.fromCharSequence { response.toString() } })

    private fun diffCommands(payload: Payload): Diff<Record> = Diff.calculate(
            getCommands(payload.key),
            payload.commands.map { Record(payload.key, it, true) },
            { _, responses -> responses },
            { response -> Differentiable.fromCharSequence { response.toString() } })

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

    private fun listenForBroadcasts() {
        disposable.add(Broadcaster.listen(
                ClientNsdService.ACTION_SOCKET_CONNECTED,
                ClientNsdService.ACTION_SOCKET_CONNECTING,
                ClientNsdService.ACTION_SOCKET_DISCONNECTED,
                ClientNsdService.ACTION_SERVER_RESPONSE,
                ClientNsdService.ACTION_START_NSD_DISCOVERY)
                .observeOn(mainThread())
                .subscribe(this::onIntentReceived) { it.printStackTrace(); listenForBroadcasts() })
    }

    private fun listenForInputPayloads() {
        isProcessing = false
        disposable.add(inPayloadProcessor.map { payload ->
            mutableListOf<State>().apply {
                val key = payload.key
                val isNew = !commands.keys.contains(key)
                val record = payload.extractRecord()
                val fetchedDevices = payload.extractDevices()
                val commandInfo = payload.extractCommandInfo()

                add(diffCommands(payload).let { State.Commands(key, isNew, it) })
                if (record != null) add(diffHistory(record).let { State.History(key, commandInfo, it) })
                if (fetchedDevices != null) add(diffDevices(fetchedDevices).let { State.Devices(key, it) })
            }
        }
                .subscribeOn(single())
                .observeOn(mainThread())
                .subscribe({ stateList ->
                    stateList.forEach {
                        when (it) {
                            is State.Devices -> devices.replace(it.diff.items)
                            is State.History -> history.replace(it.diff.items)
                            is State.Commands -> {
                                getCommands(it.key).replace(it.diff.items)
                                if (it.isNew) actualKeys.replace(commands.keys.sorted().map(::ProtocolKey))
                            }
                        }
                        stateProcessor.onNext(it)
                    }
                    if (payloadQueue.isEmpty()) isProcessing = false
                    else inPayloadProcessor.onNext(payloadQueue.poll())
                })
                { it.printStackTrace(); listenForInputPayloads() })
    }

    private fun listenForOutputPayloads() {
        disposable.add(outPayloadProcessor
                .filter { isConnected }
                .subscribe({ nsdConnection.boundService?.sendMessage(it) }) { it.printStackTrace(); listenForOutputPayloads() })
    }

    enum class Page { HOST, HISTORY, DEVICES }

    sealed class State(
            open val key: String,
            val result: DiffResult
    ) {
        class History(
                override val key: String,
                val commandInfo: ZigBeeCommandInfo?,
                internal val diff: Diff<Record>
        ) : State(key, diff.result)

        class Commands(
                override val key: String,
                val isNew: Boolean,
                internal val diff: Diff<Record>
        ) : State(key, diff.result)

        class Devices(
                override val key: String,
                internal val diff: Diff<Device>
        ) : State(key, diff.result)
    }
}

inline class ProtocolKey(val name: String) {
    val title get() = name.split(".").last().toUpperCase(Locale.US).removeSuffix("PROTOCOL")
}

