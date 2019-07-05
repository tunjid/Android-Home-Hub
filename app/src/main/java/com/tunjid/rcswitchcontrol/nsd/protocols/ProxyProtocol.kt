package com.tunjid.rcswitchcontrol.nsd.protocols

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.USBDeviceReceiver
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
            RfProtocol::class.java.name to RfProtocol(printWriter),
            KnockKnockProtocol::class.java.name to KnockKnockProtocol(printWriter)
    ).apply {
        findUsbDriver(WiredRFProtocol.ARDUINO_VENDOR_ID, WiredRFProtocol.ARDUINO_PRODUCT_ID)?.let { driver -> this[WiredRFProtocol::class.java.name] = WiredRFProtocol(driver, printWriter) }
        findUsbDriver(ZigBeeProtocol.TI_VENDOR_ID, ZigBeeProtocol.CC2531_PRODUCT_ID)?.let { driver -> this[ZigBeeProtocol::class.java.name] = ZigBeeProtocol(driver, printWriter) }
    }

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
        return Payload(CommsProtocol::class.java.name).apply { addCommand(PING) }
    }

    private fun findUsbDriver(vendorId: Int, productId: Int): UsbSerialDriver? {
        val app = App.instance
        val manager: UsbManager = app.getSystemService(Context.USB_SERVICE) as UsbManager

        val dongleLookup = ProbeTable().apply { addProduct(vendorId, productId, CdcAcmSerialDriver::class.java) }

        val drivers = UsbSerialProber(dongleLookup).findAllDrivers(manager)
        if (drivers.isEmpty()) return null

        val devices = manager.deviceList.values.filter { it.vendorId == vendorId && it.productId == productId }
        if (devices.isEmpty()) return null

        val device = devices[0]

        if (manager.hasPermission(device)) return drivers[0]

        val pending = PendingIntent.getBroadcast(app, 0, Intent(app, USBDeviceReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT)
        manager.requestPermission(device, pending)
        return null
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
