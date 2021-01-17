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

package com.tunjid.rcswitchcontrol.protocols

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
import com.rcswitchcontrol.protocols.models.Payload
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.a433mhz.protocols.BLERFProtocol
import com.tunjid.rcswitchcontrol.a433mhz.protocols.SerialRFProtocol
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.tunjid.rcswitchcontrol.common.filterIsInstance
import com.tunjid.rcswitchcontrol.di.dagger
import com.tunjid.rcswitchcontrol.models.Broadcast
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import java.io.IOException
import java.io.PrintWriter

/**
 * A protocol that proxies requests to another [CommsProtocol] of a user's choosing
 *
 *
 * Created by tj.dahunsi on 2/11/17.
 */

class ProxyProtocol(
    context: Context,
    override val printWriter: PrintWriter
) : CommsProtocol {

    private val disposable = CompositeDisposable()

    private val rfPeripheral = UsbPeripheral(
        SerialRFProtocol.ARDUINO_VENDOR_ID,
        SerialRFProtocol.ARDUINO_PRODUCT_ID,
        RF_REQUEST_CODE,
        SerialRFProtocol.key,
        protocolFunction = { SerialRFProtocol(it, printWriter) }
    )

    private val zigBeePeripheral = UsbPeripheral(
        ZigBeeProtocol.TI_VENDOR_ID,
        ZigBeeProtocol.CC2531_PRODUCT_ID,
        ZIG_BEE_REQUEST_CODE,
        ZigBeeProtocol.key,
        protocolFunction = { ZigBeeProtocol(context = context, driver = it, printWriter = printWriter) }
    )

    private val protocolMap = mutableMapOf(
        BLERFProtocol.key to BLERFProtocol(printWriter),
        KnockKnockProtocol.key to KnockKnockProtocol(printWriter)
    )

    init {
        ContextProvider.appContext.registerReceiver(USBDeviceReceiver(), IntentFilter(ACTION_USB_PERMISSION))
        context.dagger.appComponent.broadcasts()
            .filterIsInstance<Broadcast.USB.Connected>()
            .map(Broadcast.USB.Connected::device)
            .subscribe(::onUsbPermissionGranted, Throwable::printStackTrace)
            .addTo(disposable)

        Handler().apply {
            postDelayed(PERMISSION_REQUEST_DELAY) { attach(rfPeripheral) }
            postDelayed(PERMISSION_REQUEST_DELAY * 2) { attach(zigBeePeripheral) }
        }
    }

    override fun processInput(payload: Payload): Payload {
        val action = payload.action
        val protocol = protocolMap[payload.key]

        return when {
            action == CommsProtocol.pingAction -> pingAll()
            protocol != null -> protocol.processInput(payload).apply { addCommand(CommsProtocol.resetAction) }
            else -> Payload(CommsProtocol.key).apply {
                response = ContextProvider.appContext.getString(R.string.proxyprotocol_invalid_command)
                addCommand(CommsProtocol.pingAction)
            }
        }
    }

    private fun pingAll(): Payload {
        protocolMap.values.forEach { pushOut(it.processInput(CommsProtocol.pingAction.value)) }
        return Payload(CommsProtocol.key).apply { addCommand(CommsProtocol.pingAction) }
    }

    private fun attach(usbPeripheral: UsbPeripheral) = with(usbPeripheral) {
        val driver = findUsbDriver(this)
        if (driver != null) protocolMap[key] = protocolFunction(driver)
    }

    private fun onUsbPermissionGranted(device: UsbDevice) {
        for (thing in listOf(rfPeripheral, zigBeePeripheral)) if (device.vendorId == thing.vendorId && protocolMap[thing.key] == null) {
            attach(thing)
            protocolMap[thing.key]
                ?.processInput(CommsProtocol.pingAction.value)
                ?.let(::pushOut)
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
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    ?.let(Broadcast.USB::Connected)
                    ?.let(context.dagger.appComponent.broadcaster)
        }
        else -> Unit
    }
}

class UsbPeripheral(
    val vendorId: Int,
    val productId: Int,
    val requestCode: Int,
    val key: CommsProtocol.Key,
    val protocolFunction: (driver: UsbSerialDriver) -> CommsProtocol
)

private const val RF_REQUEST_CODE = 1
private const val ZIG_BEE_REQUEST_CODE = 2
private const val PERMISSION_REQUEST_DELAY = 4000L
const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
