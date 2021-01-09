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


import android.hardware.usb.UsbManager
import android.util.Base64
import android.util.Log
import androidx.core.content.getSystemService
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.Name
import com.rcswitchcontrol.protocols.models.Payload
import com.tunjid.rcswitchcontrol.a433mhz.R
import com.tunjid.rcswitchcontrol.a433mhz.models.RfSwitch
import com.tunjid.rcswitchcontrol.a433mhz.models.bytes
import com.tunjid.rcswitchcontrol.a433mhz.persistence.RfSwitchDataStore
import com.tunjid.rcswitchcontrol.a433mhz.services.ClientBleService
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.tunjid.rcswitchcontrol.common.deserialize
import java.io.PrintWriter

/**
 * A protocol for communicating with RF 433 MhZ devices over a serial port
 *
 *
 * Created by tj.dahunsi on 5/07/19.
 */

@Suppress("PrivatePropertyName")
class SerialRFProtocol constructor(
        driver: UsbSerialDriver,
       override val printWriter: PrintWriter
) : CommsProtocol, RFProtocolActions by SharedRFProtocolActions{

    private val switchStore = RfSwitchDataStore()
    private val switchCreator = RfSwitch.SwitchCreator()

    private val port: UsbSerialPort
    private val serialInputOutputManager: SerialInputOutputManager

    init {
        val manager = ContextProvider.appContext.getSystemService<UsbManager>()
        val connection = manager?.openDevice(driver.device)

        port = driver.ports[0]
        port.open(connection)
        port.setParameters(BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        serialInputOutputManager = SerialInputOutputManager(port, object : SerialInputOutputManager.Listener {
            override fun onRunError(e: Exception) = e.printStackTrace()

            override fun onNewData(rawData: ByteArray) = onSerialRead(rawData)
        })

        CommsProtocol.sharedPool.submit(serialInputOutputManager)
    }

    override fun close() {
        port.close()
        serialInputOutputManager.stop()
    }

    override fun processInput(payload: Payload): Payload {
        val output = serialRfPayload().apply { addCommand(CommsProtocol.resetAction) }

        when (val receivedAction = payload.action) {
            CommsProtocol.pingAction, refreshDevicesAction -> output.apply {
                action = ClientBleService.transmitterAction
                data = switchStore.serializedSavedSwitches
                response = (ContextProvider.appContext.getString(
                        if (receivedAction == CommsProtocol.pingAction) R.string.blercprotocol_ping_response
                        else R.string.blercprotocol_refresh_response
                ))
                addRefreshAndSniff()
            }

            sniffAction -> CommsProtocol.sharedPool.submit { port.write(byteArrayOf(SNIFF_FLAG), SERIAL_TIMEOUT) }

            renameAction -> output.apply {
                val switches = switchStore.savedSwitches
                val name = payload.data?.deserialize(Name::class)

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
                addRefreshAndSniff()
            }

            deleteAction -> output.apply {
                val switches = switchStore.savedSwitches
                val rcSwitch = payload.data?.deserialize(RfSwitch::class)
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
                addRefreshAndSniff()
            }

            ClientBleService.transmitterAction -> output.apply {
                response = ContextProvider.appContext.getString(R.string.blercprotocol_transmission_response)
                addRefreshAndSniff()

                val transmission = Base64.decode(payload.data, Base64.DEFAULT)
                CommsProtocol.sharedPool.submit { port.write(transmission, SERIAL_TIMEOUT) }
            }
        }

        return output
    }

    private fun Payload.addRefreshAndSniff() {
        addCommand(refreshDevicesAction)
        addCommand(sniffAction)
    }

    private fun onSerialRead(rawData: ByteArray) = serialRfPayload().let {
        it.addCommand(CommsProtocol.resetAction)

        when (rawData.size) {
            NOTIFICATION -> {
                Log.i("IOT", "RF NOTIFICATION: ${String(rawData)}")
                val flag = rawData.first()
                it.response = when (flag) {
                    TRANSMIT_FLAG -> ContextProvider.appContext.getString(R.string.blercprotocol_stop_sniff_response)
                    SNIFF_FLAG -> ContextProvider.appContext.getString(R.string.blercprotocol_start_sniff_response)
                    ERROR_FLAG -> ContextProvider.appContext.getString(R.string.wiredrcprotocol_invalid_command)
                    else -> String(rawData)
                }
                it.action = CommsProtocol.Action(ClientBleService.DATA_AVAILABLE_CONTROL)
                it.addCommand(refreshDevicesAction)
                if (flag != SNIFF_FLAG) it.addCommand(sniffAction)
            }
            SNIFF_PAYLOAD -> {
                Log.i("IOT", "RF SNIFF: ${String(rawData)}")
                it.data = switchCreator.state
                it.action = ClientBleService.snifferAction

                it.addRefreshAndSniff()

                when (switchCreator.state) {
                    RfSwitch.ON_CODE -> {
                        switchCreator.withOnCode(rawData)
                        it.response = ContextProvider.appContext.getString(R.string.blercprotocol_sniff_on_response)
                    }
                    RfSwitch.OFF_CODE -> {
                        val switches = switchStore.savedSwitches
                        val rcSwitch = switchCreator.withOffCode(rawData)
                        val containsSwitch = switches.map(RfSwitch::bytes).contains(rcSwitch.bytes)

                        it.response = ContextProvider.appContext.getString(
                                if (containsSwitch) R.string.scanblercprotocol_sniff_already_exists_response
                                else R.string.blercprotocol_sniff_off_response
                        )

                        if (!containsSwitch) {
                            switches.add(rcSwitch.copy(name = "Switch " + (switches.size + 1)))
                            switchStore.saveSwitches(switches)

                            it.action = (ClientBleService.transmitterAction)
                            it.data = (switchStore.serializedSavedSwitches)
                        }
                    }
                }
            }
            else -> Log.i("IOT", "RF Unknown read. Size: ${rawData.size}, as String: ${String(rawData)}")
        }

        CommsProtocol.sharedPool.submit { pushOut(it) }
        Unit
    }

    companion object {
        const val ARDUINO_VENDOR_ID = 0x2341
        const val ARDUINO_PRODUCT_ID = 0x0010

        const val BAUD_RATE = 115200
        const val SERIAL_TIMEOUT = 99999

        const val NOTIFICATION = 1
        const val SNIFF_PAYLOAD = 10

        const val ERROR_FLAG: Byte = 'E'.toByte()
        const val SNIFF_FLAG: Byte = 'R'.toByte()
        const val TRANSMIT_FLAG: Byte = 'T'.toByte()

        val key = CommsProtocol.Key(SerialRFProtocol::class.java.name)

        internal fun serialRfPayload(
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
