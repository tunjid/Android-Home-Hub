package com.tunjid.rcswitchcontrol.nsd.protocols


import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.tunjid.androidbootstrap.core.components.ServiceConnection
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster
import com.tunjid.rcswitchcontrol.data.Payload
import com.tunjid.rcswitchcontrol.data.RfSwitch
import com.tunjid.rcswitchcontrol.data.persistence.Converter.Companion.deserialize
import com.tunjid.rcswitchcontrol.data.persistence.Converter.Companion.serialize
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
class BleRcProtocol internal constructor(printWriter: PrintWriter) : CommsProtocol(printWriter) {

    private val SNIFF: String = appContext.getString(R.string.scanblercprotocol_sniff)
    private val RENAME: String = appContext.getString(R.string.blercprotocol_rename_command)
    private val DELETE: String = appContext.getString(R.string.blercprotocol_delete_command)
    private val CONNECT: String = appContext.getString(R.string.connect)
    private val DISCONNECT: String = appContext.getString(R.string.menu_disconnect)
    private val REFRESH_SWITCHES: String = appContext.getString(R.string.blercprotocol_refresh_switches_command)

    private val disposable = CompositeDisposable()

    private val switchStore = RfSwitchDataStore()
    private val switchCreator = RfSwitch.SwitchCreator()

    private val pushThread: HandlerThread = HandlerThread("PushThread").apply { start() }
    private val pushHandler: Handler = Handler(pushThread.looper)

    private val bleConnection: ServiceConnection<ClientBleService> = ServiceConnection(ClientBleService::class.java)

    init {
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

        pushThread.quitSafely()
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
                addCommand(REFRESH_SWITCHES)
                addCommand(SNIFF)
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

    private fun onBleIntentReceived(intent: Intent) {
        val intentAction = intent.action ?: return

        val payload = Payload(javaClass.name)

        when (intentAction) {
            ClientBleService.ACTION_GATT_CONNECTED -> payload.apply {
                response = appContext.getString(R.string.connected)
                addCommand(SNIFF)
                addCommand(DISCONNECT)
            }

            ClientBleService.ACTION_GATT_CONNECTING -> payload.response = appContext.getString(R.string.connecting)

            ClientBleService.ACTION_GATT_DISCONNECTED -> payload.apply {
                response = appContext.getString(R.string.disconnected)
                addCommand(CONNECT)
            }

            ClientBleService.ACTION_CONTROL -> {
                val rawData = intent.getByteArrayExtra(ClientBleService.DATA_AVAILABLE_CONTROL)
                if (rawData[0].toInt() == 1) payload.apply {
                    action = (intentAction)
                    response = (getString(R.string.blercprotocol_stop_sniff_response))
                    data = (rawData[0].toString())
                    addCommand(SNIFF)
                    addCommand(RESET)
                }
            }

            ClientBleService.ACTION_SNIFFER -> {
                val rawData = intent.getByteArrayExtra(ClientBleService.DATA_AVAILABLE_SNIFFER)
                payload.data = switchCreator.state

                when {
                    RfSwitch.ON_CODE == switchCreator.state -> payload.apply {
                        switchCreator.withOnCode(rawData)
                        action = intentAction
                        response = appContext.getString(R.string.blercprotocol_sniff_on_response)
                        addCommand(SNIFF)
                        addCommand(RESET)
                    }

                    RfSwitch.OFF_CODE == switchCreator.state -> {
                        val switches = switchStore.savedSwitches
                        val rcSwitch = switchCreator.withOffCode(rawData)
                        val containsSwitch = switches.contains(rcSwitch)

                        rcSwitch.name = "Switch " + (switches.size + 1)

                        payload.apply {
                            action = (intentAction)
                            response = getString(
                                    if (containsSwitch) R.string.scanblercprotocol_sniff_already_exists_response
                                    else R.string.blercprotocol_sniff_off_response
                            )
                            addCommand(SNIFF)
                            addCommand(REFRESH_SWITCHES)
                            addCommand(RESET)
                        }

                        if (!containsSwitch) {
                            switches.add(rcSwitch)
                            switchStore.saveSwitches(switches)
                            payload.apply {
                                action = (ClientBleService.ACTION_TRANSMITTER)
                                data = (switchStore.serializedSavedSwitches)
                            }
                        }
                    }
                }
            }
        }

        pushHandler.post { Objects.requireNonNull(printWriter).println(payload.serialize()) }
        Log.i(TAG, "Received data for: $intentAction")
    }

    companion object {

        private val TAG = BleRcProtocol::class.java.simpleName
    }
}
