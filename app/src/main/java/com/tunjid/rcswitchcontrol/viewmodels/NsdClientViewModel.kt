package com.tunjid.rcswitchcontrol.viewmodels

import android.app.Application
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.recyclerview.widget.DiffUtil.DiffResult
import com.tunjid.androidbootstrap.core.components.ServiceConnection
import com.tunjid.androidbootstrap.functions.collections.Lists
import com.tunjid.androidbootstrap.recyclerview.diff.Diff
import com.tunjid.androidbootstrap.recyclerview.diff.Differentiable
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster
import com.tunjid.rcswitchcontrol.data.*
import com.tunjid.rcswitchcontrol.data.RfSwitch.Companion.SWITCH_PREFS
import com.tunjid.rcswitchcontrol.data.persistence.Converter.Companion.deserialize
import com.tunjid.rcswitchcontrol.data.persistence.Converter.Companion.deserializeList
import com.tunjid.rcswitchcontrol.nsd.protocols.CommsProtocol
import com.tunjid.rcswitchcontrol.nsd.protocols.RfProtocol
import com.tunjid.rcswitchcontrol.nsd.protocols.ZigBeeProtocol
import com.tunjid.rcswitchcontrol.services.ClientBleService
import com.tunjid.rcswitchcontrol.services.ClientNsdService
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers.mainThread
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers.single
import java.util.*
import java.util.concurrent.TimeUnit

class NsdClientViewModel(app: Application) : AndroidViewModel(app) {

    private val disposable: CompositeDisposable = CompositeDisposable()

    private val stateProcessor: PublishProcessor<State> = PublishProcessor.create()
    private val inPayloadProcessor: PublishProcessor<Payload> = PublishProcessor.create()
    private val outPayloadProcessor: PublishProcessor<Payload> = PublishProcessor.create()
    private val connectionStateProcessor: PublishProcessor<String> = PublishProcessor.create()
    private val nsdConnection: ServiceConnection<ClientNsdService> = ServiceConnection(ClientNsdService::class.java, this::onServiceConnected)

    private val commands: MutableMap<String, MutableList<Record>> = mutableMapOf()
    private val payloadQueue: Queue<Payload> = LinkedList()
    private val history: MutableList<Record> = mutableListOf()

    val devices: MutableList<Device> = mutableListOf()

    @Volatile
    var isProcessing = false

    val keys: Set<String>
        get() = commands.keys

    val isBound: Boolean
        get() = nsdConnection.isBound

    val isConnected: Boolean
        get() = nsdConnection.isBound && !nsdConnection.boundService.isConnected

    init {
        nsdConnection.with(app).bind()
        listenForOutputPayloads()
        listenForInputPayloads()
        listenForBroadcasts()
    }

    override fun onCleared() {
        disposable.clear()
        nsdConnection.unbindService()
        super.onCleared()
    }

    fun listen(predicate: (state: State) -> Boolean = { true }): Flowable<State> = stateProcessor.filter(predicate)

    fun dispatchPayload(key: String, payloadReceiver: Payload.() -> Unit) = dispatchPayload(key, { true }, payloadReceiver)

    fun onBackground() = nsdConnection.boundService.onAppBackground()

    fun getCommands(key: String?) = if (key == null) history else commands.getOrPut(key) { mutableListOf() }

    fun lastIndex(key: String?): Int = getCommands(key).size - 1

    fun forgetService() {
        // Don't call unbind, when the hosting activity is finished,
        // onDestroy will be called and the connection unbound
        if (nsdConnection.isBound) nsdConnection.boundService.stopSelf()

        getApplication<Application>().getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE).edit()
                .remove(ClientNsdService.LAST_CONNECTED_SERVICE).apply()
    }

    fun connectionState(): Flowable<String> = connectionStateProcessor.startWith({
        val bound = nsdConnection.isBound
        if (bound) nsdConnection.boundService.onAppForeGround()

        getConnectionText(
                if (bound) nsdConnection.boundService.connectionState
                else ClientNsdService.ACTION_SOCKET_DISCONNECTED)

    }()).observeOn(mainThread())

    private fun onServiceConnected(service: ClientNsdService) {
        connectionStateProcessor.onNext(getConnectionText(service.connectionState))
        dispatchPayload(CommsProtocol::class.java.name, commands::isEmpty) { action = CommsProtocol.PING }
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
        val isBound = nsdConnection.isBound

        when (newState) {
            ClientNsdService.ACTION_SOCKET_CONNECTED -> {
                dispatchPayload(CommsProtocol::class.java.name, commands::isEmpty) { action = (CommsProtocol.PING) }
                text = if (!isBound) context.getString(R.string.connected)
                else context.getString(R.string.connected_to, nsdConnection.boundService.serviceName)
            }

            ClientNsdService.ACTION_SOCKET_CONNECTING -> text =
                    if (!isBound) context.getString(R.string.connecting)
                    else context.getString(R.string.connecting_to, nsdConnection.boundService.serviceName)

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

    private fun diffHistory(payload: Payload): Diff<Record> = Diff.calculate(
            history,
            listOf(Record(payload.key, payload.getMessage(), true)),
            { current, responses -> current.apply { addAll(responses) } },
            { response -> Differentiable.fromCharSequence { response.toString() } })

    private fun diffCommands(payload: Payload): Diff<Record> = Diff.calculate(
            getCommands(payload.key),
            payload.commands.map { Record(payload.key, it, true) },
            { _, responses -> responses },
            { response -> Differentiable.fromCharSequence { response.toString() } })

    private fun Payload.getMessage(): String {
        val read = response ?: return "Blank response"
        return if (read.isBlank()) "Blank response" else read
    }

    private fun Payload.extractCommandInfo(): ZigBeeCommandInfo? {
        if (RfProtocol::class.java.name == key) return null
        if (extractDevices() != null) return null
        return data?.deserialize(ZigBeeCommandInfo::class)
    }

    private fun Payload.extractDevices(): List<Device>? {
        val serialized = data ?: return null
        val context = getApplication<Application>()

        return when (key) {
            RfProtocol::class.java.name -> when (action) {
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
        disposable.add(inPayloadProcessor.map { payload ->
                    mutableListOf<State>().apply {
                        val key = payload.key
                        val isNew = !keys.contains(key)
                        val fetchedDevices = payload.extractDevices()
                        val commandInfo = payload.extractCommandInfo()

                        add(diffHistory(payload).let { State.History(key, history, it) })
                        add(diffCommands(payload).let { State.Commands(key, isNew, getCommands(key), it) })
                        if (fetchedDevices != null) add(diffDevices(fetchedDevices).let { State.Devices(key, commandInfo, devices, it) })
                    }
                }
                .subscribeOn(single())
                .observeOn(mainThread())
                .subscribe({ stateList ->
                    stateList.forEach {
                        when (it) {
                            is State.History -> Lists.replace(it.current, it.diff.items)
                            is State.Commands -> Lists.replace(it.current, it.diff.items)
                            is State.Devices -> Lists.replace(it.current, it.diff.items)
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
                .sample(200, TimeUnit.MILLISECONDS)
                .filter { nsdConnection.isBound }
                .subscribe(nsdConnection.boundService::sendMessage) { it.printStackTrace(); listenForOutputPayloads() })
    }


    sealed class State(
            open val key: String,
            val result: DiffResult
    ) {
        class History(
                override val key: String,
                internal val current: List<Record>,
                internal val diff: Diff<Record>
        ) : State(key, diff.result)

        class Commands(
                override val key: String,
                val isNew: Boolean,
                internal val current: List<Record>,
                internal val diff: Diff<Record>
        ) : State(key, diff.result)

        class Devices(
                override val key: String,
                val commandInfo: ZigBeeCommandInfo?,
                internal val current: List<Device>,
                internal val diff: Diff<Device>
        ) : State(key, diff.result)
    }
}