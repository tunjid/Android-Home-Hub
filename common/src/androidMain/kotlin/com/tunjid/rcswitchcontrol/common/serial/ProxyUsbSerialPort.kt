package com.tunjid.rcswitchcontrol.common.serial

import android.hardware.usb.UsbDevice
import com.tunjid.rcswitchcontrol.common.SerialInfo

interface ProxyUsbSerialPort {
    fun open(info: SerialInfo, device: UsbDevice, onDataAvailable: (ByteArray) -> Unit)

    fun write(data: ByteArray)

    fun close()
}