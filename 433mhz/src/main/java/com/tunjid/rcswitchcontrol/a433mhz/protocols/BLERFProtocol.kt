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

package com.tunjid.rcswitchcontrol.a433mhz.protocols

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.CommsProtocol.Companion.sharedDispatcher
import com.rcswitchcontrol.protocols.Name
import com.rcswitchcontrol.protocols.models.Payload
import com.tunjid.androidx.communications.bluetooth.BLEScanner
import com.tunjid.androidx.communications.bluetooth.ScanFilterCompat
import com.tunjid.androidx.core.components.services.HardServiceConnection
import com.tunjid.rcswitchcontrol.a433mhz.R
import com.tunjid.rcswitchcontrol.a433mhz.models.RfSwitch
import com.tunjid.rcswitchcontrol.a433mhz.models.bytes
import com.tunjid.rcswitchcontrol.a433mhz.persistence.RfSwitchDataStore
import com.tunjid.rcswitchcontrol.a433mhz.services.ClientBleService
import com.tunjid.rcswitchcontrol.a433mhz.services.ClientBleService.Companion.C_HANDLE_CONTROL
import com.tunjid.rcswitchcontrol.a433mhz.services.ClientBleService.Companion.STATE_SNIFFING
import com.tunjid.rcswitchcontrol.common.Broadcaster
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.tunjid.rcswitchcontrol.common.deserialize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import java.io.PrintWriter
import java.util.*

/**
 * A protocol for communicating with RF 433 MhZ devices
 *
 *
 * Created by tj.dahunsi on 2/11/17.
 */

@Suppress("PrivatePropertyName")
class BLERFProtocol constructor(override val printWriter: PrintWriter) : CommsProtocol, RFProtocolActions by SharedRFProtocolActions {

    override val scope = CoroutineScope(SupervisorJob() + sharedDispatcher)
    private val deviceMap = HashMap<CommsProtocol.Action, BluetoothDevice>()

    private val switchStore = RfSwitchDataStore()
    private val switchCreator = RfSwitch.SwitchCreator()

    private val bleConnection = HardServiceConnection(ContextProvider.appContext, ClientBleService::class.java)

    private val scanner: BLEScanner
    private val scanHandler = Handler(Looper.getMainLooper())

    private val isConnected: Boolean
        get() = bleConnection.boundService?.isConnected == true

    init {
        val bluetoothManager = ContextProvider.appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (!bluetoothAdapter.isEnabled) bluetoothAdapter.enable()

        scanner = BLEScanner.getBuilder(bluetoothAdapter)
            .addFilter(ScanFilterCompat.getBuilder()
                .setServiceUuid(ParcelUuid(UUID.fromString(ClientBleService.DATA_TRANSCEIVER_SERVICE)))
                .build())
            .withCallBack {
                val record = it.scanRecord
                val device = it.device
                val deviceName = record?.deviceName?.let(CommsProtocol::Action)

                if (deviceName != null) deviceMap[deviceName] = device
            }
            .build()

        bleConnection.bind()
        Broadcaster.listen(
            ClientBleService.gattConnectedAction.value,
            ClientBleService.gattConnectingAction.value,
            ClientBleService.gattDisconnectedAction.value,
            ClientBleService.gattServicesDiscoveredAction.value,
            ClientBleService.controlAction.value,
            ClientBleService.snifferAction.value,
            ClientBleService.DATA_AVAILABLE_UNKNOWN)
            .asFlow()
            .onEach(this::onBleIntentReceived)
            .catch { it.printStackTrace() }
            .launchIn(scope)
    }

    override fun close() {
        scope.cancel()
        bleConnection.unbindService()
    }

    override suspend fun processInput(payload: Payload): Payload {
        val output = bleRfPayload().apply { addCommand(CommsProtocol.resetAction) }

        when (val receivedAction = payload.action) {
            CommsProtocol.pingAction, refreshDevicesAction -> output.apply {
                response = (ContextProvider.appContext.getString(
                    if (receivedAction == CommsProtocol.pingAction) R.string.blercprotocol_ping_response
                    else R.string.blercprotocol_refresh_response
                ))
                action = ClientBleService.transmitterAction
                data = switchStore.serializedSavedSwitches

                if (isConnected) {
                    addCommand(refreshDevicesAction)
                    addCommand(sniffAction)
                } else addCommand(scanAction)
            }

            scanAction -> {
                deviceMap.clear()
                scanHandler.post(scanner::startScan)
                scanHandler.postDelayed(this::onScanComplete, SCAN_DURATION.toLong())

                output.response = ContextProvider.appContext.getString(R.string.scanblercprotocol_start_scan_reponse)
            }

            disconnectAction -> bleConnection.boundService?.disconnect()

            in deviceMap -> {
                val extras = Bundle()
                extras.putParcelable(ClientBleService.BLUETOOTH_DEVICE, deviceMap[receivedAction])

                bleConnection.start { replaceExtras(extras) }
                bleConnection.bind { replaceExtras(extras) }
            }

            sniffAction -> output.apply {
                response = ContextProvider.appContext.getString(R.string.blercprotocol_start_sniff_response)
                addCommand(CommsProtocol.resetAction)
                addCommand(disconnectAction)
                bleConnection.boundService?.writeCharacteristicArray(C_HANDLE_CONTROL, byteArrayOf(STATE_SNIFFING))
            }

            renameAction -> output.apply {
                val switches = switchStore.savedSwitches
                val name = payload.data?.deserialize<Name>()

                val position = switches.map(RfSwitch::id).indexOf(name?.id)
                val hasSwitch = position > -1

                response = if (hasSwitch && name != null)
                    ContextProvider.appContext.getString(R.string.blercprotocol_renamed_response, switches[position].id, name.value)
                else
                    ContextProvider.appContext.getString(R.string.blercprotocol_no_such_switch_response)

                // Switches are equal based on their codes, not their names.
                // Remove the switch with the old name, and add the switch with the new name.
                if (hasSwitch && name != null) {
                    val switch = switches.removeAt(position)
                    switches.add(position, switch.copy(name = name.value))
                    switchStore.saveSwitches(switches)
                }

                action = receivedAction
                data = switchStore.serializedSavedSwitches
                addCommand(sniffAction)
            }

            deleteAction -> output.apply {
                val switches = switchStore.savedSwitches
                val rcSwitch = payload.data?.deserialize<RfSwitch>()
                val removed = switches.filterNot { it.bytes.contentEquals(rcSwitch?.bytes) }

                val response = if (rcSwitch == null || switches.size == removed.size)
                    ContextProvider.appContext.getString(R.string.blercprotocol_no_such_switch_response)
                else
                    ContextProvider.appContext.getString(R.string.blercprotocol_deleted_response, rcSwitch.id)

                // Save switches before sending them
                switchStore.saveSwitches(removed)

                output.response = response
                action = receivedAction
                data = switchStore.serializedSavedSwitches
                addCommand(sniffAction)
            }

            ClientBleService.transmitterAction -> output.apply {
                response = ContextProvider.appContext.getString(R.string.blercprotocol_transmission_response)
                addCommand(sniffAction)
                addCommand(refreshDevicesAction)

                Broadcaster.push(Intent(ClientBleService.transmitterAction.value)
                    .putExtra(ClientBleService.DATA_AVAILABLE_TRANSMITTER, payload.data))
            }
        }

        return output
    }

    private fun onBleIntentReceived(intent: Intent) = bleRfPayload().run {
        val intentAction = intent.action?.let(CommsProtocol::Action) ?: return@run

        addCommand(CommsProtocol.resetAction)

        when (intentAction) {
            ClientBleService.gattConnectedAction -> {
                response = ContextProvider.appContext.getString(R.string.connected)
                addCommand(refreshDevicesAction)
                addCommand(sniffAction)
                addCommand(disconnectAction)
            }
            ClientBleService.gattConnectingAction -> {
                response = ContextProvider.appContext.getString(R.string.connecting)
                addCommand(scanAction)
            }
            ClientBleService.gattDisconnectedAction -> {
                response = ContextProvider.appContext.getString(R.string.disconnected)
                addCommand(scanAction)
            }

            ClientBleService.controlAction -> {
                val rawData = intent.getByteArrayExtra(ClientBleService.DATA_AVAILABLE_CONTROL)
                    ?: return@run
                if (stoppedSniffing(rawData)) {
                    action = intentAction
                    response = ContextProvider.appContext.getString(R.string.blercprotocol_stop_sniff_response)
                    data = (rawData[0].toString())

                    addCommand(refreshDevicesAction)
                    addCommand(sniffAction)
                }
            }

            ClientBleService.snifferAction -> {
                val rawData = intent.getByteArrayExtra(ClientBleService.DATA_AVAILABLE_SNIFFER)
                    ?: return@run
                data = switchCreator.state

                when (switchCreator.state) {
                    RfSwitch.ON_CODE -> {
                        switchCreator.withOnCode(rawData)
                        action = intentAction
                        response = ContextProvider.appContext.getString(R.string.blercprotocol_sniff_on_response)
                        addCommand(sniffAction)
                    }
                    RfSwitch.OFF_CODE -> {
                        val switches = switchStore.savedSwitches
                        val rcSwitch = switchCreator.withOffCode(rawData)
                        val containsSwitch = switches.map(RfSwitch::bytes).contains(rcSwitch.bytes)

                        action = (intentAction)
                        response = ContextProvider.appContext.getString(
                            if (containsSwitch) R.string.scanblercprotocol_sniff_already_exists_response
                            else R.string.blercprotocol_sniff_off_response
                        )
                        addCommand(refreshDevicesAction)
                        addCommand(sniffAction)

                        if (!containsSwitch) {
                            switches.add(rcSwitch.copy(name = "Switch " + (switches.size + 1)))

                            switchStore.saveSwitches(switches)
                            action = (ClientBleService.transmitterAction)
                            data = (switchStore.serializedSavedSwitches)
                        }
                    }
                }
            }
        }

        scope.launch(sharedDispatcher) { pushOut(this@run) }
        Log.i(TAG, "Received data for: $intentAction")
    }

    private fun stoppedSniffing(rawData: ByteArray) = rawData[0].toInt() == 1

    private fun onScanComplete() {
        scanner.stopScan()

        val resources = ContextProvider.appContext.resources
        val output = bleRfPayload()
        output.addCommand(CommsProtocol.resetAction)
        output.addCommand(scanAction)

        for (device in deviceMap.values) output.addCommand(CommsProtocol.Action(device.name))

        output.response = resources.getString(R.string.scanblercprotocol_scan_response, deviceMap.size)
        scope.launch(sharedDispatcher) { pushOut(output) }
    }

    companion object {

        private val TAG = BLERFProtocol::class.java.simpleName
        private const val SCAN_DURATION = 5000

        val key = CommsProtocol.Key(BLERFProtocol::class.java.name)

        internal fun bleRfPayload(
            data: String? = null,
            action: CommsProtocol.Action? = null,
            response: String? = null
        ) = Payload(
            key = key,
            data = data,
            action = action,
            response = response
        )
    }
}
