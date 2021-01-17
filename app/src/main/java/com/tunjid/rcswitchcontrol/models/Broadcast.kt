package com.tunjid.rcswitchcontrol.models

import android.hardware.usb.UsbDevice
import android.net.nsd.NsdServiceInfo
import com.tunjid.rcswitchcontrol.client.Status

sealed class Broadcast {
    sealed class ClientNsd : Broadcast() {
        object Stop : ClientNsd()
        data class StartDiscovery(val service: NsdServiceInfo? = null) : ClientNsd()
        data class ConnectionStatus(val status: Status) : ClientNsd()
        data class ServerResponse(val data: String) : ClientNsd()
    }

    sealed class ServerNsd : Broadcast() {
        object Stop : ServerNsd()
    }

    sealed class USB : Broadcast() {
        data class Connected(val device: UsbDevice) : USB()
    }
}