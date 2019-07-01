package com.tunjid.rcswitchcontrol.nsd.protocols

import android.content.Context
import android.hardware.usb.UsbManager
import androidx.annotation.StringRes
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.data.Payload
import java.io.IOException
import java.io.PrintWriter

/**
 * A protocol that proxies requests to another [CommsProtocol] of a user's choosing
 *
 *
 * Created by tj.dahunsi on 2/11/17.
 */

class ProxyProtocol(printWriter: PrintWriter) : CommsProtocol(printWriter) {

    private var choosing: Boolean = false
    private var protocol: CommsProtocol? = null
    private val protocolMap = mutableMapOf(
            RC_REMOTE to BleRcProtocol(printWriter),
            KNOCK_KNOCK to KnockKnockProtocol(printWriter)
    ).apply { findZigBeeDriver()?.let { driver -> this[ZIGBEE_CONTROLLER] = ZigBeeProtocol(driver, printWriter) } }

    override fun processInput(payload: Payload): Payload {
        var output = Payload(payload.key)

        val action = payload.action

        when (action) {
            PING -> protocol?.let { return it.processInput(payload) }
                    ?: return closeAndChoose(output)
            RESET_CURRENT -> return protocol?.processInput(RESET)
                    ?: return closeAndChoose(output, true)
            RESET, CHOOSER -> return closeAndChoose(output)
        }

        if (!choosing) return protocol?.processInput(payload) ?: closeAndChoose(output)

        // Choose the protocol to proxy through
        protocol = when (action) {
            CONNECT_RC_REMOTE -> protocolMap.getOrPut(action) { ScanBleRcProtocol(printWriter) }
            RC_REMOTE -> protocolMap.getOrPut(action) { BleRcProtocol(printWriter) }
            KNOCK_KNOCK -> protocolMap.getOrPut(action) { KnockKnockProtocol(printWriter) }
            ZIGBEE_CONTROLLER -> findZigBeeDriver()?.let { protocolMap.getOrPut(action) { ZigBeeProtocol(it, printWriter) } }
                    ?: return output.apply { invalidCommand(R.string.zigbeeprotocol_unavailable) }
            else -> return output.apply { invalidCommand(R.string.proxyprotocol_invalid_command) }
        }

        protocol?.also { protocol ->
            choosing = false

            var result = "Chose Protocol: " + protocol.javaClass.simpleName
            result += "\n"

            val delegatedPayload = protocol.processInput(PING)

            output = output.copy(key = delegatedPayload.key)
            delegatedPayload.data?.let { output.data = it }
            delegatedPayload.action?.let { output.action = it }
            output.response = result + delegatedPayload.response

            for (command in delegatedPayload.commands) output.addCommand(command)

            output.addCommand(CHOOSER)
            output.addCommand(RESET_CURRENT)
            output.addCommand(RESET)
        }

        return output
    }

    private fun closeAndChoose(payload: Payload, resetProtocol: Boolean = false): Payload {
        if (resetProtocol) protocol?.processInput(RESET)
        choosing = true

        return payload.apply {
            response = appContext.getString(R.string.proxyprotocol_ping_response)
            addCommand(ZIGBEE_CONTROLLER)
            addCommand(KNOCK_KNOCK)
            addCommand(RC_REMOTE)
            addCommand(RESET_CURRENT)
            addCommand(RESET)

            if (App.isAndroidThings) payload.addCommand(CONNECT_RC_REMOTE)
        }
    }

    private fun findZigBeeDriver(): UsbSerialDriver? {
        val manager: UsbManager = App.instance.getSystemService(Context.USB_SERVICE) as UsbManager

        val dongleLookup = ProbeTable().apply { addProduct(ZigBeeProtocol.TI_VENDOR_ID, ZigBeeProtocol.CC2531_PRODUCT_ID, CdcAcmSerialDriver::class.java) }

        val drivers = UsbSerialProber(dongleLookup).findAllDrivers(manager)

        return if (drivers.isEmpty()) null else drivers[0]
    }

    private fun Payload.invalidCommand(@StringRes responseRes: Int) {
        response = getString(responseRes)
        if (App.isAndroidThings) addCommand(CONNECT_RC_REMOTE)

        addCommand(KNOCK_KNOCK)
        addCommand(RC_REMOTE)
        addCommand(RESET)
    }

    @Throws(IOException::class)
    override fun close() {
        protocolMap.values.forEach {
            try {
                it.close()
            } catch (exception: IOException) {
                exception.printStackTrace()
            }
        }
        protocol = null
    }

    companion object {

        private const val CHOOSER = "choose"
        private const val RESET_CURRENT = "Reset current"
        private const val KNOCK_KNOCK = "Knock Knock Jokes"
        private const val RC_REMOTE = "Control Remote Device"
        private const val CONNECT_RC_REMOTE = "Connect Remote Device"
        private const val ZIGBEE_CONTROLLER = "Control Zigbee Devices"

    }
}
