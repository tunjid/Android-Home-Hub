package com.tunjid.rcswitchcontrol.common.serialport

import android.hardware.usb.UsbDevice
import com.tunjid.rcswitchcontrol.common.SerialInfo

expect interface UsbSerial

interface ProxyUsbSerialPort {
    fun open(info: SerialInfo, device: UsbSerial, onDataAvailable: (ByteArray) -> Unit)

    fun write(data: ByteArray)

    fun close()
}
