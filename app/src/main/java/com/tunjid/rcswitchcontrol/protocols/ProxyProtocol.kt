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
import android.os.Looper
import androidx.core.content.getSystemService
import androidx.core.os.postDelayed
import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.models.Payload
import com.rcswitchcontrol.zigbee.protocol.Dongle
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.a433mhz.protocols.BLERFProtocol
import com.tunjid.rcswitchcontrol.a433mhz.protocols.SerialRFProtocol
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.tunjid.rcswitchcontrol.common.SerialInfo
import com.tunjid.rcswitchcontrol.common.asSuspend
import com.tunjid.rcswitchcontrol.common.findUsbDevice
import com.tunjid.rcswitchcontrol.di.dagger
import com.tunjid.rcswitchcontrol.models.Broadcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.reactive.asFlow
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

    override val scope = CoroutineScope(SupervisorJob() + CommsProtocol.sharedDispatcher)

    private val plausiblePeripherals = listOf(
        UsbPeripheral(
            requestCode = RF_REQUEST_CODE,
            serialInfo = SerialRFProtocol.ArduinoSerialInfo,
            key = SerialRFProtocol.key,
            protocolFunction = { info, device -> SerialRFProtocol(info, device, printWriter) }
        ),
        UsbPeripheral(
            requestCode = ZIG_BEE_REQUEST_CODE,
            serialInfo = Dongle.Companion.cc2531SerialInfo,
            key = ZigBeeProtocol.key,
            protocolFunction = { _, device ->
                ZigBeeProtocol(
                    context = context,
                    dongle = Dongle.CC2531(device),
                    printWriter = printWriter
                )
            }
        ),
        UsbPeripheral(
            requestCode = ZIG_BEE_REQUEST_CODE,
            serialInfo = Dongle.Companion.emberThunderBoard2SerialInfo,
            key = ZigBeeProtocol.key,
            protocolFunction = { _, device ->
                ZigBeeProtocol(
                    context = context,
                    dongle = Dongle.SiLabsThunderBoard2(device),
                    printWriter = printWriter
                )
            }
        ),
    )

    private val protocolMap = mutableMapOf(
        BLERFProtocol.key to BLERFProtocol(printWriter),
        KnockKnockProtocol.key to KnockKnockProtocol(printWriter)
    )

    private val lazyPeripherals by lazy {
        with(Handler(Looper.getMainLooper())) {
            plausiblePeripherals.forEachIndexed { index, usbPeripheral ->
                postDelayed(PERMISSION_REQUEST_DELAY * (index + 1)) { attach(usbPeripheral) }
            }
        }
    }

    init {
        ContextProvider.appContext.registerReceiver(USBDeviceReceiver(), IntentFilter(ACTION_USB_PERMISSION))
        context.dagger.appComponent.broadcasts()
            .asFlow()
            .filterIsInstance<Broadcast.USB.Connected>()
            .map(Broadcast.USB.Connected::device.asSuspend)
            .onEach(::onUsbPermissionGranted)
            .catch { it.printStackTrace() }
            .launchIn(scope)
    }

    override suspend fun processInput(payload: Payload): Payload {
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

    private suspend fun pingAll(): Payload {
        lazyPeripherals
        protocolMap.values.forEach { pushOut(it.processInput(CommsProtocol.pingAction.value)) }
        return Payload(CommsProtocol.key).apply { addCommand(CommsProtocol.pingAction) }
    }

    private fun attach(usbPeripheral: UsbPeripheral) = with(usbPeripheral) {
        val device = findUsbDevice(this)
        if (device != null) protocolMap[key] = protocolFunction(usbPeripheral.serialInfo, device)
    }

    private suspend fun onUsbPermissionGranted(device: UsbDevice) {
        for (thing in plausiblePeripherals) if (device.vendorId == thing.serialInfo.vendorId && protocolMap[thing.key] == null) {
            attach(thing)
            protocolMap[thing.key]
                ?.processInput(CommsProtocol.pingAction.value)
                ?.let { pushOut(it) }
        }
    }

    private fun findUsbDevice(usbPeripheral: UsbPeripheral): UsbDevice? = usbPeripheral.run {
        val app = ContextProvider.appContext
        val manager = app.getSystemService<UsbManager>() ?: return@run null

        val device = app.findUsbDevice(usbPeripheral.serialInfo) ?: return@run null

        if (manager.hasPermission(device)) return device

        val pending = PendingIntent.getBroadcast(app, requestCode, Intent(ACTION_USB_PERMISSION), FLAG_CANCEL_CURRENT)
        manager.requestPermission(device, pending)
        return null
    }

    @Throws(IOException::class)
    override fun close() {
        scope.cancel()
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
    val requestCode: Int,
    val serialInfo: SerialInfo,
    val key: CommsProtocol.Key,
    val protocolFunction: (info: SerialInfo, device: UsbDevice) -> CommsProtocol
)

private const val RF_REQUEST_CODE = 1
private const val ZIG_BEE_REQUEST_CODE = 2
private const val PERMISSION_REQUEST_DELAY = 4000L
const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
