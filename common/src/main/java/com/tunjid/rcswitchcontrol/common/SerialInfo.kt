package com.tunjid.rcswitchcontrol.common

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.getSystemService

data class SerialInfo(
    val vendorId: Int,
    val productId: Int,
    val baudRate: Int
)

fun Context.findUsbDevice(info: SerialInfo): UsbDevice? =
    getSystemService<UsbManager>()
    ?.deviceList
    ?.filterValues { device ->
        device.vendorId == info.vendorId && device.productId == info.productId
    }
    ?.toList()
    ?.firstOrNull()
    ?.second