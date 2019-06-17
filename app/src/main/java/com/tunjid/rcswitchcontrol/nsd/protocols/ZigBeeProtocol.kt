package com.tunjid.rcswitchcontrol.nsd.protocols

import android.content.Context.USB_SERVICE
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.model.Payload
import com.zsmartsystems.zigbee.dongle.cc2531.ZigBeeDongleTiCc2531
import java.io.PrintWriter
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread


class ZigBeeProtocol(printWriter: PrintWriter) : CommsProtocol(printWriter) {

    internal var wiresharkFileLength = Integer.MAX_VALUE
    internal var sequence = 0
    internal var captureMillis: Long = 0
    internal var restartTimer: Long = 30000

    internal var clientPort: Int = 0
    internal var wiresharkCounter = 0
    internal var startTime = System.nanoTime()
    internal var timezone: Long = 0

    internal var channelId: Int? = null
    internal lateinit var wiresharkFilename: String

    internal lateinit var dongle: ZigBeeDongleTiCc2531

    internal lateinit var client: DatagramSocket
    internal lateinit var address: InetAddress


    init {
        val manager: UsbManager = App.instance.getSystemService(USB_SERVICE) as UsbManager

        val customTable = ProbeTable()
        customTable.addProduct(1105, 5800, CdcAcmSerialDriver::class.java)

        val drivers = UsbSerialProber(customTable).findAllDrivers(manager)


        if (drivers.isNotEmpty()) {
            val driver = drivers[0]
            Log.i("TEST", "Initializing $driver")

            thread {
                val dongle = ZigBeeDongleTiCc2531(AndroidSerialPort(driver, 9600))
                Log.i("TEST", "Initialization status: ${dongle.initialize()}")
            }
        }

    }

    override fun processInput(payload: Payload): Payload {
        val builder = Payload.builder()
        builder.setKey(javaClass.name).addCommand(RESET)

        return builder.build()
    }

    override fun close() {
    }
}