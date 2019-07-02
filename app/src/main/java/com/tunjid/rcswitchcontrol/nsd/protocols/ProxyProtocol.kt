package com.tunjid.rcswitchcontrol.nsd.protocols

import android.content.Context
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.data.Payload
import com.tunjid.rcswitchcontrol.data.persistence.Converter.Companion.serialize
import java.io.IOException
import java.io.PrintWriter

/**
 * A protocol that proxies requests to another [CommsProtocol] of a user's choosing
 *
 *
 * Created by tj.dahunsi on 2/11/17.
 */

class ProxyProtocol(printWriter: PrintWriter) : CommsProtocol(printWriter) {

    private val protocolMap = mutableMapOf(
            BleRcProtocol::class.java.name to BleRcProtocol(printWriter),
            KnockKnockProtocol::class.java.name to KnockKnockProtocol(printWriter)
    ).apply { findZigBeeDriver()?.let { driver -> this[ZigBeeProtocol::class.java.name] = ZigBeeProtocol(driver, printWriter) } }

    override fun processInput(payload: Payload): Payload {
        val action = payload.action
        val protocol = protocolMap[payload.key]

        return when {
            action == PING -> pingAll()
            protocol != null -> protocol.processInput(payload).apply { addCommand(RESET) }
            else -> Payload(CommsProtocol::class.java.name).apply {
                response = getString(R.string.proxyprotocol_invalid_command)
                addCommand(PING)
            }
        }
    }

    private fun pingAll(): Payload {
        protocolMap.values.forEach { printWriter.println(it.processInput(PING).serialize()) }
        return Payload(CommsProtocol::class.java.name)
    }

    private fun findZigBeeDriver(): UsbSerialDriver? {
        val manager: UsbManager = App.instance.getSystemService(Context.USB_SERVICE) as UsbManager

        val dongleLookup = ProbeTable().apply { addProduct(ZigBeeProtocol.TI_VENDOR_ID, ZigBeeProtocol.CC2531_PRODUCT_ID, CdcAcmSerialDriver::class.java) }

        val drivers = UsbSerialProber(dongleLookup).findAllDrivers(manager)

        return if (drivers.isEmpty()) null else drivers[0]
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
    }
}
