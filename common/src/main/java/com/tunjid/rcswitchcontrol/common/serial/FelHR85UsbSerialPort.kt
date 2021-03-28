package com.tunjid.rcswitchcontrol.common.serial

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.getSystemService
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import com.felhr.usbserial.UsbSerialInterface.UsbReadCallback
import com.tunjid.rcswitchcontrol.common.SerialInfo

class FelHR85UsbSerialPort(
//    private val serialInfo: SerialInfo,
//    private val usbDevice: UsbDevice,
    private val context: Context
) : ProxyUsbSerialPort {

    private var serial: UsbSerialDevice? = null

    override fun open(info: SerialInfo, device: UsbDevice, onDataAvailable: (ByteArray) -> Unit) {
        val readCallback = UsbReadCallback(onDataAvailable)
        val manager = context.getSystemService<UsbManager>()

        val connection = manager?.openDevice(device)
            ?: // You probably need to call UsbManager.requestPermission(driver.getDevice(), ..)
            return

        val serialDevice = UsbSerialDevice.createUsbSerialDevice(device, connection)
        serial = serialDevice

        serialDevice.open()
        serialDevice.setBaudRate(info.baudRate)
        serialDevice.setDataBits(UsbSerialInterface.DATA_BITS_8)
        serialDevice.setStopBits(UsbSerialInterface.STOP_BITS_1)
        serialDevice.setParity(UsbSerialInterface.PARITY_NONE)
        serialDevice.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF)

        serialDevice.read(readCallback)
    }

    override fun write(data: ByteArray) {
        serial?.write(data)
    }

    override fun close() {
        serial?.close()
    }
}