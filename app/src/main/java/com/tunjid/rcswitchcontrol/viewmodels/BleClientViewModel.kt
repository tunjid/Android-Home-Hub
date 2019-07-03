package com.tunjid.rcswitchcontrol.viewmodels

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.tunjid.androidbootstrap.core.components.ServiceConnection
import com.tunjid.androidbootstrap.functions.collections.Lists
import com.tunjid.androidbootstrap.material.animator.FabExtensionAnimator
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster
import com.tunjid.rcswitchcontrol.data.Device
import com.tunjid.rcswitchcontrol.data.RfSwitch
import com.tunjid.rcswitchcontrol.data.RfSwitch.Companion.SWITCH_PREFS
import com.tunjid.rcswitchcontrol.data.persistence.RfSwitchDataStore
import com.tunjid.rcswitchcontrol.services.ClientBleService
import com.tunjid.rcswitchcontrol.services.ClientBleService.Companion.BLUETOOTH_DEVICE
import com.tunjid.rcswitchcontrol.services.ClientBleService.Companion.C_HANDLE_CONTROL
import com.tunjid.rcswitchcontrol.services.ClientBleService.Companion.C_HANDLE_TRANSMITTER
import com.tunjid.rcswitchcontrol.services.ClientBleService.Companion.STATE_SNIFFING
import com.tunjid.rcswitchcontrol.services.ClientNsdService
import com.tunjid.rcswitchcontrol.services.ServerNsdService
import com.tunjid.rcswitchcontrol.services.ServerNsdService.Companion.SERVICE_NAME_KEY
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers.mainThread
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.processors.PublishProcessor
import java.util.*

class BleClientViewModel(application: Application) : AndroidViewModel(application) {

    private var device: BluetoothDevice? = null

    private val switchStore = RfSwitchDataStore()
    private val switchCreator: RfSwitch.SwitchCreator = RfSwitch.SwitchCreator()

    private val disposable: CompositeDisposable = CompositeDisposable()
    private val bleConnectedProcessor: PublishProcessor<String> = PublishProcessor.create()
    private val loadingProcessor: PublishProcessor<Boolean> = PublishProcessor.create()
    private val serverConnectedProcessor: PublishProcessor<Boolean> = PublishProcessor.create()

    private val bleConnection: ServiceConnection<ClientBleService> = ServiceConnection(ClientBleService::class.java, ServiceConnection.BindCallback<ClientBleService> { this.onBleServiceConnected(it) })
    private val serverConnection: ServiceConnection<ServerNsdService> = ServiceConnection(ServerNsdService::class.java) { serverConnectedProcessor.onNext(true) }

    val switches: MutableList<Device> = ArrayList(switchStore.savedSwitches)

    val isBleBound: Boolean
        get() = bleConnection.isBound

    val isBleConnected: Boolean
        get() = bleConnection.isBound && !bleConnection.boundService.isConnected

    val fabState: FabExtensionAnimator.GlyphState
        get() {
            val context = getApplication<Application>()

            return when {
                switchCreator.state == RfSwitch.ON_CODE -> FabExtensionAnimator.newState(context.getString(R.string.sniff_code, context.getString(R.string.on)), ContextCompat.getDrawable(context, R.drawable.ic_on_24dp))
                else -> FabExtensionAnimator.newState(context.getString(R.string.sniff_code, context.getString(R.string.off)), ContextCompat.getDrawable(context, R.drawable.ic_off_24dp))
            }
        }

    init {
        bleConnection.with(application).bind()

        disposable.add(Broadcaster.listen(
                ClientBleService.ACTION_GATT_CONNECTED,
                ClientBleService.ACTION_GATT_CONNECTING,
                ClientBleService.ACTION_GATT_DISCONNECTED,
                ClientBleService.ACTION_GATT_SERVICES_DISCOVERED,
                ClientBleService.ACTION_CONTROL,
                ClientBleService.ACTION_SNIFFER,
                ClientBleService.DATA_AVAILABLE_UNKNOWN
        ).subscribe(this::onIntentReceived, Throwable::printStackTrace))
    }

    override fun onCleared() {
        super.onCleared()

        if (bleConnection.isBound) bleConnection.boundService.onAppBackground()
        serverConnection.unbindService()
        bleConnection.unbindService()

        loadingProcessor.onComplete()
        bleConnectedProcessor.onComplete()
        serverConnectedProcessor.onComplete()
        disposable.clear()
    }

    fun listenBle(device: BluetoothDevice): Flowable<Boolean> {
        this.device = device
        val extras = Bundle()
        extras.putParcelable(BLUETOOTH_DEVICE, device)

        bleConnection.with(getApplication()).setExtras(extras).bind()

        return loadingProcessor.observeOn(mainThread())
    }

    fun listenServer(): Flowable<Boolean> {
        val context = getApplication<Application>()
        if (context.getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE).getBoolean(ServerNsdService.SERVER_FLAG, false)) {
            serverConnection.with(context).start()
            serverConnection.with(context).bind()
        }

        return serverConnectedProcessor.startWith(serverConnection.isBound).observeOn(mainThread())
    }

    fun connectionState(): Flowable<String> {
        return bleConnectedProcessor.startWith { publisher ->
            val bound = bleConnection.isBound
            if (bound) bleConnection.boundService.onAppForeGround()

            publisher.onNext(getConnectionText(if (bound)
                bleConnection.boundService.connectionState
            else
                ClientNsdService.ACTION_SOCKET_DISCONNECTED))
            publisher.onComplete()
        }.observeOn(mainThread())
    }

    fun reconnectBluetooth() {
        bleConnection.boundService.connect(device)
    }

    fun disconnectBluetooth() {
        bleConnection.boundService.disconnect()
    }

    fun refreshSwitches() {
        Lists.replace(switches, switchStore.savedSwitches.map { it as Device })
    }

    fun sniffRcSwitch() {
        if (bleConnection.isBound)
            bleConnection.boundService
                    .writeCharacteristicArray(C_HANDLE_CONTROL, byteArrayOf(STATE_SNIFFING))
    }

    fun toggleSwitch(rfSwitch: RfSwitch, state: Boolean) {
        if (bleConnection.isBound)
            bleConnection.boundService
                    .writeCharacteristicArray(C_HANDLE_TRANSMITTER, rfSwitch.getTransmission(state))
    }

    fun onSwitchUpdated(rfSwitch: RfSwitch): Int {
        saveSwitches()
        return switches.indexOf(rfSwitch)
    }

    fun forgetBluetoothDevice() {
        Broadcaster.push(Intent(ServerNsdService.ACTION_STOP))
        val clientBleService = bleConnection.boundService

        clientBleService.disconnect()
        clientBleService.close()

        getApplication<Application>().getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE).edit()
                .remove(ClientBleService.LAST_PAIRED_DEVICE)
                .remove(ServerNsdService.SERVER_FLAG).apply()
    }

    fun restartServer() {
        if (serverConnection.isBound) serverConnection.boundService.restart()
    }

    fun nameServer(name: String) {
        val context = getApplication<Application>()

        context.getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE)
                .edit().putString(SERVICE_NAME_KEY, name)
                .putBoolean(ServerNsdService.SERVER_FLAG, true).apply()

        serverConnection.with(context).start()
        serverConnection.with(context).bind()
    }

    private fun onBleServiceConnected(service: ClientBleService) {
        getConnectionText(service.connectionState)
    }

    private fun onIntentReceived(intent: Intent) {
        val action = intent.action ?: return

        val rawData: ByteArray
        when (action) {
            ClientBleService.ACTION_GATT_CONNECTED, ClientBleService.ACTION_GATT_CONNECTING, ClientBleService.ACTION_GATT_DISCONNECTED -> bleConnectedProcessor.onNext(getConnectionText(action))
            ClientBleService.ACTION_CONTROL -> {
                rawData = intent.getByteArrayExtra(ClientBleService.DATA_AVAILABLE_CONTROL)
                loadingProcessor.onNext(rawData[0].toInt() == 0)
            }
            ClientBleService.ACTION_SNIFFER -> {
                rawData = intent.getByteArrayExtra(ClientBleService.DATA_AVAILABLE_SNIFFER)
                when (switchCreator.state) {
                    RfSwitch.ON_CODE -> switchCreator.withOnCode(rawData)
                    RfSwitch.OFF_CODE -> {
                        val rcSwitch = switchCreator.withOffCode(rawData)
                        rcSwitch.name = "Switch " + (switches.size + 1)

                        if (switches.contains(rcSwitch)) return

                        switches.add(rcSwitch)
                        saveSwitches()
                    }
                }
                loadingProcessor.onNext(false)
            }
        }
    }

    private fun getConnectionText(newState: String): String {
        val context = getApplication<Application>()
        return when (newState) {
            ClientBleService.ACTION_GATT_CONNECTED -> context.getString(R.string.connected)
            ClientBleService.ACTION_GATT_CONNECTING -> context.getString(R.string.connecting)
            ClientBleService.ACTION_GATT_DISCONNECTED -> context.getString(R.string.disconnected)
            else -> ""
        }
    }

    fun saveSwitches() = switchStore.saveSwitches(switches.filter { it is RfSwitch }.map { it as RfSwitch })
}
