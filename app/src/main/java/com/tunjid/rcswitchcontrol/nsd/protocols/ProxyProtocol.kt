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

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_CANCEL_CURRENT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import androidx.core.content.getSystemService
import androidx.core.os.postDelayed
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.ContextProvider
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster
import com.rcswitchcontrol.protocols.models.Payload
import io.reactivex.disposables.CompositeDisposable
import java.io.IOException
import java.io.PrintWriter

/**
 * A protocol that proxies requests to another [CommsProtocol] of a user's choosing
 *
 *
 * Created by tj.dahunsi on 2/11/17.
 */

class ProxyProtocol(private val printWriter: PrintWriter) : CommsProtocol(printWriter) {

    private val disposable = CompositeDisposable()

    private val rfPeripheral = UsbPeripheral(
            SerialRFProtocol.ARDUINO_VENDOR_ID,
            SerialRFProtocol.ARDUINO_PRODUCT_ID,
            RF_REQUEST_CODE,
            SerialRFProtocol::class.java.name,
            protocolFunction = { SerialRFProtocol(it, printWriter) }
    )

    private val zigBeePeripheral = UsbPeripheral(
            ZigBeeProtocol.TI_VENDOR_ID,
            ZigBeeProtocol.CC2531_PRODUCT_ID,
            ZIG_BEE_REQUEST_CODE,
            ZigBeeProtocol::class.java.name,
            protocolFunction = { ZigBeeProtocol(it, printWriter) }
    )

    private val protocolMap = mutableMapOf(
            BLERFProtocol::class.java.name to BLERFProtocol(printWriter),
            KnockKnockProtocol::class.java.name to KnockKnockProtocol(printWriter)
    )

    init {
        ContextProvider.appContext.registerReceiver(USBDeviceReceiver(), IntentFilter(ACTION_USB_PERMISSION))
        disposable.add(Broadcaster.listen(ACTION_USB_PERMISSION).subscribe(::onUsbPermissionGranted, Throwable::printStackTrace))

        Handler().postDelayed(PERMISSION_REQUEST_DELAY) { attach(rfPeripheral); attach(zigBeePeripheral) }
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
        protocolMap.values.forEach { pushOut(it.processInput(PING)) }
        return Payload(CommsProtocol::class.java.name).apply { addCommand(PING) }
    }


    private fun attach(usbPeripheral: UsbPeripheral) = with(usbPeripheral) {
        val driver = findUsbDriver(this)
        if (driver != null) protocolMap[name] = protocolFunction(driver)
    }

    private fun onUsbPermissionGranted(it: Intent) {
        val device: UsbDevice = it.getParcelableExtra(UsbManager.EXTRA_DEVICE) ?: return
        for (thing in listOf(rfPeripheral, zigBeePeripheral)) if (device.vendorId == thing.vendorId && protocolMap[thing.name] == null) {
            attach(thing)
            pingAll()
        }
    }

    private fun findUsbDriver(usbPeripheral: UsbPeripheral): UsbSerialDriver? = usbPeripheral.run {
        val app = ContextProvider.appContext
        val manager = app.getSystemService<UsbManager>() ?: return@run null

        val dongleLookup = ProbeTable().apply { addProduct(vendorId, productId, CdcAcmSerialDriver::class.java) }

        val drivers = UsbSerialProber(dongleLookup).findAllDrivers(manager)
        if (drivers.isEmpty()) return null

        val devices = manager.deviceList.values.filter { it.vendorId == vendorId && it.productId == productId }
        if (devices.isEmpty()) return null

        val device = devices[0]

        if (manager.hasPermission(device)) return drivers[0]

        val pending = PendingIntent.getBroadcast(app, requestCode, Intent(ACTION_USB_PERMISSION), FLAG_CANCEL_CURRENT)
        manager.requestPermission(device, pending)
        return null
    }

    @Throws(IOException::class)
    override fun close() {
        disposable.clear()
        protocolMap.values.forEach {
            try {
                it.close()
            } catch (exception: IOException) {
                exception.printStackTrace()
            }
        }
    }
}

class USBDeviceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = when (ACTION_USB_PERMISSION) {
        intent.action -> synchronized(this) {
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) Broadcaster.push(intent)
        }
        else -> Unit
    }
}

class UsbPeripheral(
        val vendorId: Int,
        val productId: Int,
        val requestCode: Int,
        val name: String,
        val protocolFunction: (driver: UsbSerialDriver) -> CommsProtocol
)

private const val RF_REQUEST_CODE = 1
private const val ZIG_BEE_REQUEST_CODE = 2
private const val PERMISSION_REQUEST_DELAY = 4000L
const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
