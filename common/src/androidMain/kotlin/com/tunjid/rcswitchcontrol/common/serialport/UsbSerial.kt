package com.tunjid.rcswitchcontrol.common.serialport

import android.hardware.usb.UsbDevice

actual interface UsbSerial {
    val device: UsbDevice
}