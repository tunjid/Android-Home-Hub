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

package com.tunjid.rcswitchcontrol.nsd.protocols


import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.tunjid.androidbootstrap.communications.bluetooth.BLEScanner
import com.tunjid.androidbootstrap.communications.bluetooth.ScanFilterCompat
import com.tunjid.androidbootstrap.core.components.ServiceConnection
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster
import com.tunjid.rcswitchcontrol.data.Payload
import com.tunjid.rcswitchcontrol.data.RfSwitch
import com.tunjid.rcswitchcontrol.data.persistence.Converter.Companion.deserialize
import com.tunjid.rcswitchcontrol.data.persistence.RfSwitchDataStore
import com.tunjid.rcswitchcontrol.services.ClientBleService
import com.tunjid.rcswitchcontrol.services.ClientBleService.Companion.C_HANDLE_CONTROL
import com.tunjid.rcswitchcontrol.services.ClientBleService.Companion.STATE_SNIFFING
import io.reactivex.disposables.CompositeDisposable
import java.io.PrintWriter
import java.util.*

/**
 * A protocol for communicating with RF 433 MhZ devices
 *
 *
 * Created by tj.dahunsi on 2/11/17.
 */

@Suppress("PrivatePropertyName")
class BLERFProtocol internal constructor(printWriter: PrintWriter) : CommsProtocol(printWriter) {

    private val SCAN: String = appContext.getString(R.string.button_scan)
    private val SNIFF: String = appContext.getString(R.string.scanblercprotocol_sniff)
    private val RENAME: String = appContext.getString(R.string.blercprotocol_rename_command)
    private val DELETE: String = appContext.getString(R.string.blercprotocol_delete_command)
    private val DISCONNECT: String = appContext.getString(R.string.menu_disconnect)
    private val REFRESH_SWITCHES: String = appContext.getString(R.string.blercprotocol_refresh_switches_command)

    private val deviceMap = HashMap<String, BluetoothDevice>()
    private val disposable = CompositeDisposable()

    private val switchStore = RfSwitchDataStore()
    private val switchCreator = RfSwitch.SwitchCreator()

    private val bleConnection: ServiceConnection<ClientBleService> = ServiceConnection(ClientBleService::class.java)

    private val scanner: BLEScanner
    private val scanHandler = Handler(Looper.getMainLooper())

    private val isConnected: Boolean
        get() = bleConnection.isBound && bleConnection.boundService.isConnected

    init {
        val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (!bluetoothAdapter.isEnabled) bluetoothAdapter.enable()

        scanner = BLEScanner.getBuilder(bluetoothAdapter)
                .addFilter(ScanFilterCompat.getBuilder()
                        .setServiceUuid(ParcelUuid(UUID.fromString(ClientBleService.DATA_TRANSCEIVER_SERVICE)))
                        .build())
                .withCallBack {
                    val record = it.scanRecord
                    val device = it.device
                    val deviceName = record?.deviceName

                    if (deviceName != null) deviceMap[deviceName] = device
                }
                .build()

        bleConnection.with(appContext).bind()
        disposable.add(Broadcaster.listen(
                ClientBleService.ACTION_GATT_CONNECTED,
                ClientBleService.ACTION_GATT_CONNECTING,
                ClientBleService.ACTION_GATT_DISCONNECTED,
                ClientBleService.ACTION_GATT_SERVICES_DISCOVERED,
                ClientBleService.ACTION_CONTROL,
                ClientBleService.ACTION_SNIFFER,
                ClientBleService.DATA_AVAILABLE_UNKNOWN)
                .subscribe(this::onBleIntentReceived, Throwable::printStackTrace))
    }

    override fun close() {
        disposable.clear()
        if (bleConnection.isBound) bleConnection.unbindService()
    }

    override fun processInput(payload: Payload): Payload {
        val output = Payload(javaClass.name).apply { addCommand(RESET) }

        when (val receivedAction = payload.action) {
            PING, REFRESH_SWITCHES -> output.apply {
                response = (getString(
                        if (receivedAction == PING) R.string.blercprotocol_ping_response
                        else R.string.blercprotocol_refresh_response
                ))
                action = ClientBleService.ACTION_TRANSMITTER
                data = switchStore.serializedSavedSwitches

                if (isConnected) {
                    addCommand(REFRESH_SWITCHES)
                    addCommand(SNIFF)
                } else addCommand(SCAN)
            }

            SCAN -> {
                deviceMap.clear()
                scanHandler.post(scanner::startScan)
                scanHandler.postDelayed(this::onScanComplete, SCAN_DURATION.toLong())

                output.response = getString(R.string.scanblercprotocol_start_scan_reponse)
            }

            DISCONNECT -> if (bleConnection.isBound) bleConnection.boundService.disconnect()

            in deviceMap -> {
                val extras = Bundle()
                extras.putParcelable(ClientBleService.BLUETOOTH_DEVICE, deviceMap[receivedAction])

                bleConnection.with(appContext).setExtras(extras).start()
                bleConnection.with(appContext).setExtras(extras).bind()
            }

            SNIFF -> output.apply {
                response = appContext.getString(R.string.blercprotocol_start_sniff_response)
                addCommand(RESET)
                addCommand(DISCONNECT)
                if (bleConnection.isBound) bleConnection.boundService
                        .writeCharacteristicArray(C_HANDLE_CONTROL, byteArrayOf(STATE_SNIFFING))
            }

            RENAME -> output.apply {
                val switches = switchStore.savedSwitches
                val rcSwitch = payload.data?.deserialize(RfSwitch::class)

                val position = switches.indexOf(rcSwitch)
                val hasSwitch = position > -1

                response = if (hasSwitch && rcSwitch != null)
                    getString(R.string.blercprotocol_renamed_response, switches[position].name, rcSwitch.name)
                else
                    getString(R.string.blercprotocol_no_such_switch_response)

                // Switches are equal based on their codes, not their names.
                // Remove the switch with the old name, and add the switch with the new name.
                if (hasSwitch && rcSwitch != null) {
                    switches.removeAt(position)
                    switches.add(position, rcSwitch)
                    switchStore.saveSwitches(switches)
                }

                action = receivedAction
                data = switchStore.serializedSavedSwitches
                addCommand(SNIFF)
            }

            DELETE -> output.apply {
                val switches = switchStore.savedSwitches
                val rcSwitch = payload.data?.deserialize(RfSwitch::class)
                val response = if (rcSwitch == null || !switches.remove(rcSwitch))
                    getString(R.string.blercprotocol_no_such_switch_response)
                else
                    getString(R.string.blercprotocol_deleted_response, rcSwitch.name)

                // Save switches before sending them
                switchStore.saveSwitches(switches)

                output.response = response
                action = receivedAction
                data = switchStore.serializedSavedSwitches
                addCommand(SNIFF)
            }

            ClientBleService.ACTION_TRANSMITTER -> output.apply {
                response = getString(R.string.blercprotocol_transmission_response)
                addCommand(SNIFF)
                addCommand(REFRESH_SWITCHES)

                Broadcaster.push(Intent(ClientBleService.ACTION_TRANSMITTER)
                        .putExtra(ClientBleService.DATA_AVAILABLE_TRANSMITTER, payload.data))
            }
        }

        return output
    }

    private fun onBleIntentReceived(intent: Intent) = Payload(javaClass.name).run {
        val intentAction = intent.action ?: return@run

        addCommand(RESET)

        when (intentAction) {
            ClientBleService.ACTION_GATT_CONNECTED -> {
                response = appContext.getString(R.string.connected)
                addCommand(REFRESH_SWITCHES)
                addCommand(SNIFF)
                addCommand(DISCONNECT)
            }
            ClientBleService.ACTION_GATT_CONNECTING -> {
                response = appContext.getString(R.string.connecting)
                addCommand(SCAN)
            }
            ClientBleService.ACTION_GATT_DISCONNECTED -> {
                response = appContext.getString(R.string.disconnected)
                addCommand(SCAN)
            }

            ClientBleService.ACTION_CONTROL -> {
                val rawData = intent.getByteArrayExtra(ClientBleService.DATA_AVAILABLE_CONTROL)
                if (stoppedSniffing(rawData)) {
                    action = (intentAction)
                    response = (getString(R.string.blercprotocol_stop_sniff_response))
                    data = (rawData[0].toString())

                    addCommand(REFRESH_SWITCHES)
                    addCommand(SNIFF)
                }
            }

            ClientBleService.ACTION_SNIFFER -> {
                val rawData = intent.getByteArrayExtra(ClientBleService.DATA_AVAILABLE_SNIFFER)
                data = switchCreator.state

                when {
                    RfSwitch.ON_CODE == switchCreator.state -> {
                        switchCreator.withOnCode(rawData)
                        action = intentAction
                        response = appContext.getString(R.string.blercprotocol_sniff_on_response)
                        addCommand(SNIFF)
                    }

                    RfSwitch.OFF_CODE == switchCreator.state -> {
                        val switches = switchStore.savedSwitches
                        val rcSwitch = switchCreator.withOffCode(rawData)
                        val containsSwitch = switches.contains(rcSwitch)

                        rcSwitch.name = "Switch " + (switches.size + 1)

                        action = (intentAction)
                        response = getString(
                                if (containsSwitch) R.string.scanblercprotocol_sniff_already_exists_response
                                else R.string.blercprotocol_sniff_off_response
                        )
                        addCommand(REFRESH_SWITCHES)
                        addCommand(SNIFF)

                        if (!containsSwitch) {
                            switches.add(rcSwitch)

                            switchStore.saveSwitches(switches)
                            action = (ClientBleService.ACTION_TRANSMITTER)
                            data = (switchStore.serializedSavedSwitches)
                        }
                    }
                }
            }
        }

        sharedPool.submit { pushOut(this) }
        Log.i(TAG, "Received data for: $intentAction")
    }

    private fun stoppedSniffing(rawData: ByteArray) = rawData[0].toInt() == 1

    private fun onScanComplete() {
        scanner.stopScan()

        val resources = appContext.resources
        val output = Payload(this.javaClass.name)
        output.addCommand(RESET)
        output.addCommand(SCAN)

        for (device in deviceMap.values) output.addCommand(device.name)

        output.response = resources.getString(R.string.scanblercprotocol_scan_response, deviceMap.size)
        pushOut(output)
    }

    companion object {

        private val TAG = BLERFProtocol::class.java.simpleName
        private const val SCAN_DURATION = 5000

    }
}
