package com.tunjid.rcswitchcontrol.viewmodels

import android.app.Application
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.recyclerview.widget.DiffUtil.DiffResult
import com.tunjid.androidbootstrap.core.components.ServiceConnection
import com.tunjid.androidbootstrap.functions.Supplier
import com.tunjid.androidbootstrap.functions.collections.Lists
import com.tunjid.androidbootstrap.recyclerview.diff.Diff
import com.tunjid.androidbootstrap.recyclerview.diff.Differentiable
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster
import com.tunjid.rcswitchcontrol.data.*
import com.tunjid.rcswitchcontrol.data.RcSwitch.Companion.SWITCH_PREFS
import com.tunjid.rcswitchcontrol.data.persistence.Converter.Companion.deserialize
import com.tunjid.rcswitchcontrol.data.persistence.Converter.Companion.deserializeList
import com.tunjid.rcswitchcontrol.nsd.protocols.BleRcProtocol
import com.tunjid.rcswitchcontrol.nsd.protocols.CommsProtocol
import com.tunjid.rcswitchcontrol.nsd.protocols.ZigBeeProtocol
import com.tunjid.rcswitchcontrol.services.ClientBleService
import com.tunjid.rcswitchcontrol.services.ClientNsdService
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers.mainThread
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers.io

class NsdClientViewModel(app: Application) : AndroidViewModel(app) {

    val history: MutableList<String> = mutableListOf()
    val commands: MutableList<String> = mutableListOf()
    val devices: MutableList<Device> = mutableListOf()

    private val noisyCommands: Set<String> = setOf(
            ClientBleService.ACTION_TRANSMITTER,
            app.getString(R.string.scanblercprotocol_sniff),
            app.getString(R.string.blercprotocol_rename_command),
            app.getString(R.string.blercprotocol_delete_command),
            app.getString(R.string.blercprotocol_refresh_switches_command)
    )

    private val disposable: CompositeDisposable = CompositeDisposable()

    private val stateProcessor: PublishProcessor<State> = PublishProcessor.create()
    private val payloadProcessor: PublishProcessor<Payload> = PublishProcessor.create()
    private val connectionStateProcessor: PublishProcessor<String> = PublishProcessor.create()
    private val nsdConnection: ServiceConnection<ClientNsdService> = ServiceConnection(ClientNsdService::class.java, this::onServiceConnected)

    val isBound: Boolean
        get() = nsdConnection.isBound

    val isConnected: Boolean
        get() = nsdConnection.isBound && !nsdConnection.boundService.isConnected

    val latestHistoryIndex: Int
        get() = history.size - 1

    init {
        nsdConnection.with(app).bind()
        listenForBroadcasts()
        listenForPayloads()
    }

    override fun onCleared() {
        disposable.clear()
        nsdConnection.unbindService()
        super.onCleared()
    }

    fun listen(predicate: (state: State) -> Boolean = { true }): Flowable<State> = stateProcessor.filter(predicate)

    fun sendMessage(message: Payload) = sendMessage(Supplier { true }, message)

    fun onBackground() = nsdConnection.boundService.onAppBackground()

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
        sendMessage(Supplier { commands.isEmpty() }, Payload.builder().setAction(CommsProtocol.PING).build())
    }

    private fun onIntentReceived(intent: Intent) {
        when (val action = intent.action) {
            ClientNsdService.ACTION_SOCKET_CONNECTED,
            ClientNsdService.ACTION_SOCKET_CONNECTING,
            ClientNsdService.ACTION_SOCKET_DISCONNECTED -> connectionStateProcessor.onNext(getConnectionText(action))
            ClientNsdService.ACTION_START_NSD_DISCOVERY -> connectionStateProcessor.onNext(getConnectionText(ClientNsdService.ACTION_SOCKET_CONNECTING))
            ClientNsdService.ACTION_SERVER_RESPONSE -> {
                val serverResponse = intent.getStringExtra(ClientNsdService.DATA_SERVER_RESPONSE)
                        ?: return
                val payload = serverResponse.deserialize(Payload::class)

                payloadProcessor.onNext(payload)
            }
        }
    }

    private fun getConnectionText(newState: String): String {
        var text = ""
        val context = getApplication<Application>()
        val isBound = nsdConnection.isBound

        when (newState) {
            ClientNsdService.ACTION_SOCKET_CONNECTED -> {
                sendMessage(Supplier { commands.isEmpty() }, Payload.builder().setAction(CommsProtocol.PING).build())
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

    private fun sendMessage(predicate: Supplier<Boolean>, message: Payload) {
        if (nsdConnection.isBound && predicate.get()) nsdConnection.boundService.sendMessage(message)
    }

    private fun diffDevices(devices: List<Device>): Diff<Device> = Diff.calculate(this.devices, devices) { current, server ->
        mutableSetOf<Device>().apply {
            addAll(current)
            addAll(server)
        }.toList()
    }

    private fun diffHistory(payload: Payload): Diff<String> = Diff.calculate(
            history,
            listOf(payload.response ?: "Unknown response"),
            { current, responses -> current.apply { addAll(responses) } },
            { response -> Differentiable.fromCharSequence { response.toString() } })

    private fun <T> diff(list: List<T>, diffSupplier: Supplier<Diff<T>>): Single<DiffResult> {
        return Single.fromCallable<Diff<T>>(diffSupplier::get)
                .subscribeOn(io())
                .observeOn(mainThread())
                .doOnSuccess { diff -> Lists.replace(list, diff.items) }
                .map { diff -> diff.result }
    }

    private fun Payload.getMessage(): String? {
        response ?: return null
        return if (noisyCommands.contains(action)) response else null
    }

    private fun Payload.extractCommandInfo(): ZigBeeCommandInfo? {
        if (BleRcProtocol::class.java.name == key) return null
        if (extractDevices() != null) return null
        return data?.deserialize(ZigBeeCommandInfo::class)
    }

    private fun Payload.extractDevices(): List<Device>? {
        val serialized = data ?: return null
        val context = getApplication<Application>()

        return when (key) {
            BleRcProtocol::class.java.name -> when (action) {
                ClientBleService.ACTION_TRANSMITTER,
                context.getString(R.string.blercprotocol_delete_command),
                context.getString(R.string.blercprotocol_rename_command) -> serialized.deserializeList(RcSwitch::class)
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
                .subscribe(this::onIntentReceived) { it.printStackTrace(); listenForBroadcasts() })
    }

    private fun listenForPayloads() {
        disposable.add(payloadProcessor.concatMap { payload ->
            Single.concat(mutableListOf<Single<DiffResult>>().let { singleList ->
                val devices = payload.extractDevices()

                singleList.add(diff(history, Supplier { diffHistory(payload) }))
                if (devices != null) singleList.add(diff(this.devices, Supplier { diffDevices(devices) }))

                singleList.map { single -> single.map { State(devices != null, payload.getMessage(), payload.extractCommandInfo(), payload.commands, it) } }
            })
        }
                .observeOn(mainThread())
                .doOnNext { Lists.replace(commands, it.commands) }
                .subscribe(stateProcessor::onNext) { it.printStackTrace(); listenForPayloads() })
    }

    class State internal constructor(
            val isRc: Boolean,
            val prompt: String?,
            val commandInfo: ZigBeeCommandInfo?,
            val commands: Set<String>,
            val result: DiffResult
    )
}