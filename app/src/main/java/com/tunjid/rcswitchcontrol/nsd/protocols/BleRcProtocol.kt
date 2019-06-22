package com.tunjid.rcswitchcontrol.nsd.protocols


import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.tunjid.androidbootstrap.core.components.ServiceConnection
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster
import com.tunjid.rcswitchcontrol.data.Payload
import com.tunjid.rcswitchcontrol.data.RcSwitch
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

    private val disposable: CompositeDisposable = CompositeDisposable()
    private val switchCreator: RcSwitch.SwitchCreator = RcSwitch.SwitchCreator()

    private val pushThread: HandlerThread = HandlerThread("PushThread").apply { start() }
    private val pushHandler: Handler= Handler(pushThread.looper)

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
        val builder = Payload.builder()
        builder.setKey(javaClass.name).addCommand(RESET)

        when (val action = payload.action) {
            PING, REFRESH_SWITCHES -> builder.setResponse(getString(
                    if (action == PING) R.string.blercprotocol_ping_response
                    else R.string.blercprotocol_refresh_response
            ))
                    .setAction(ClientBleService.ACTION_TRANSMITTER)
                    .setData(RcSwitch.serializedSavedSwitches)
                    .addCommand(REFRESH_SWITCHES).addCommand(SNIFF)

            SNIFF -> {
                builder.addCommand(RESET)
                        .addCommand(DISCONNECT)
                        .setResponse(appContext.getString(R.string.blercprotocol_start_sniff_response))

                if (bleConnection.isBound) bleConnection.boundService
                        .writeCharacteristicArray(C_HANDLE_CONTROL, byteArrayOf(STATE_SNIFFING))
            }

            RENAME -> {
                val switches = RcSwitch.savedSwitches
                val rcSwitch = payload.data?.let { RcSwitch.deserialize(it) }

                val position = switches.indexOf(rcSwitch)
                val hasSwitch = position > -1

                builder.setResponse(if (hasSwitch && rcSwitch != null)
                    getString(R.string.blercprotocol_renamed_response, switches[position].name, rcSwitch.name)
                else
                    getString(R.string.blercprotocol_no_such_switch_response))

                // Switches are equal based on their codes, not their names.
                // Remove the switch with the old name, and add the switch with the new name.
                if (hasSwitch && rcSwitch != null) {
                    switches.removeAt(position)
                    switches.add(position, rcSwitch)
                    RcSwitch.saveSwitches(switches)
                }

                builder.setData(RcSwitch.serializedSavedSwitches)
                        .addCommand(SNIFF)
                        .setAction(action)
            }

            DELETE -> {
                val switches = RcSwitch.savedSwitches
                val rcSwitch = payload.data?.let { RcSwitch.deserialize(it) }
                val response = if (rcSwitch == null || !switches.remove(rcSwitch))
                    getString(R.string.blercprotocol_no_such_switch_response)
                else
                    getString(R.string.blercprotocol_deleted_response, rcSwitch.name)

                // Save switches before sending them
                RcSwitch.saveSwitches(switches)

                builder.setResponse(response).setAction(action)
                        .setData(RcSwitch.serializedSavedSwitches)
                        .addCommand(SNIFF)
            }

            ClientBleService.ACTION_TRANSMITTER -> {
                builder.setResponse(getString(R.string.blercprotocol_transmission_response))
                        .addCommand(SNIFF)
                        .addCommand(REFRESH_SWITCHES)

                Broadcaster.push(Intent(ClientBleService.ACTION_TRANSMITTER)
                        .putExtra(ClientBleService.DATA_AVAILABLE_TRANSMITTER, payload.data))
            }
        }

        return builder.build()
    }

    private fun onBleIntentReceived(intent: Intent) {
        val action = intent.action ?: return

        val builder = Payload.builder()
        builder.setKey(this@BleRcProtocol.javaClass.name)

        when (action) {
            ClientBleService.ACTION_GATT_CONNECTED -> builder.addCommand(SNIFF)
                    .addCommand(DISCONNECT)
                    .setResponse(appContext.getString(R.string.connected))

            ClientBleService.ACTION_GATT_CONNECTING -> builder.setResponse(appContext.getString(R.string.connecting))

            ClientBleService.ACTION_GATT_DISCONNECTED -> builder.addCommand(CONNECT)
                    .setResponse(appContext.getString(R.string.disconnected))

            ClientBleService.ACTION_CONTROL -> {
                val rawData = intent.getByteArrayExtra(ClientBleService.DATA_AVAILABLE_CONTROL)
                if (rawData[0].toInt() == 1)
                    builder.setResponse(getString(R.string.blercprotocol_stop_sniff_response))
                            .setAction(action)
                            .setData(rawData[0].toString())
                            .addCommand(SNIFF)
                            .addCommand(RESET)
            }

            ClientBleService.ACTION_SNIFFER -> {
                val rawData = intent.getByteArrayExtra(ClientBleService.DATA_AVAILABLE_SNIFFER)
                builder.setData(switchCreator.state)

                when {
                    RcSwitch.ON_CODE == switchCreator.state -> {
                        switchCreator.withOnCode(rawData)
                        builder.setResponse(appContext.getString(R.string.blercprotocol_sniff_on_response))
                                .setAction(action)
                                .addCommand(SNIFF)
                                .addCommand(RESET)
                    }

                    RcSwitch.OFF_CODE == switchCreator.state -> {
                        val switches = RcSwitch.savedSwitches
                        val rcSwitch = switchCreator.withOffCode(rawData)
                        val containsSwitch = switches.contains(rcSwitch)

                        rcSwitch.name = "Switch " + (switches.size + 1)

                        builder.setAction(action)
                                .addCommand(SNIFF)
                                .addCommand(REFRESH_SWITCHES)
                                .addCommand(RESET)
                                .setResponse(getString(
                                        if (containsSwitch) R.string.scanblercprotocol_sniff_already_exists_response
                                        else R.string.blercprotocol_sniff_off_response)
                                )

                        if (!containsSwitch) {
                            switches.add(rcSwitch)
                            RcSwitch.saveSwitches(switches)
                            builder.setAction(ClientBleService.ACTION_TRANSMITTER)
                                    .setData(RcSwitch.serializedSavedSwitches)
                        }
                    }
                }
            }
        }

        pushHandler.post { Objects.requireNonNull(printWriter).println(builder.build().serialize()) }
        Log.i(TAG, "Received data for: $action")
    }

    companion object {

        private val TAG = BleRcProtocol::class.java.simpleName
    }
}
