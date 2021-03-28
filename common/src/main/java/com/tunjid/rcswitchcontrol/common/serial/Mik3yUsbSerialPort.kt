package com.tunjid.rcswitchcontrol.common.serial

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.getSystemService
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.tunjid.rcswitchcontrol.common.SerialInfo

private data class State(
    val serialPort: UsbSerialPort,
    val serialThread: HandlerThread,
    val serialHandler: Handler,
    val serialInputOutputManager: SerialInputOutputManager,
)

class Mik3yUsbSerialPort(
//    private val serialInfo: SerialInfo,
//    private val usbDevice: UsbDevice,
    private val context: Context,
    private val writeTimeout: Int = 9999999
) : ProxyUsbSerialPort {

    private var state: State? = null

    override fun open(info: SerialInfo, device: UsbDevice, onDataAvailable: (ByteArray) -> Unit) {
        val serialListener = object : SerialInputOutputManager.Listener {
            override fun onRunError(e: java.lang.Exception?) = e?.printStackTrace() ?: Unit

            override fun onNewData(data: ByteArray?) {
                data?.let(onDataAvailable)
            }
        }

        if (state != null) throw RuntimeException("Serial port already open.")

        val driver = CdcAcmSerialDriver(device)
        val manager = context.getSystemService<UsbManager>()

        val connection = manager?.openDevice(device)
            ?: // You probably need to call UsbManager.requestPermission(driver.getDevice(), ..)
            return

        val port = driver.ports[0]

        try {
            port.open(connection)
            port.setParameters(info.baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            val serialInputOutputManager = SerialInputOutputManager(port, serialListener)
            val serialThread = HandlerThread("serialThread").apply { start() }
            val serialHandler = Handler(serialThread.looper)

            serialHandler.post(serialInputOutputManager)

            state = State(
                serialPort = port,
                serialThread = serialThread,
                serialHandler = serialHandler,
                serialInputOutputManager = serialInputOutputManager
            )
        } catch (e: Exception) {
            throw RuntimeException("Failed to open serial port: $driver", e)
        }
    }

    override fun write(data: ByteArray) {
        state?.serialPort?.write(data, writeTimeout)
    }

    override fun close() {
        state?.let {
            it.serialPort.close()
            it.serialInputOutputManager.stop()
            it.serialThread.quitSafely()

        }
    }
}