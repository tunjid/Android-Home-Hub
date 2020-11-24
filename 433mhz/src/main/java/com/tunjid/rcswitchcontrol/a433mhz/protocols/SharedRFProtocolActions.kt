package com.tunjid.rcswitchcontrol.a433mhz.protocols

import com.rcswitchcontrol.protocols.CommonDeviceActions
import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.DeviceActions
import com.tunjid.rcswitchcontrol.a433mhz.R
import com.tunjid.rcswitchcontrol.common.ContextProvider

internal interface RFProtocolActions : DeviceActions {
    val scanAction: CommsProtocol.Action
    val disconnectAction: CommsProtocol.Action
    val sniffAction: CommsProtocol.Action
}

internal object SharedRFProtocolActions : RFProtocolActions, DeviceActions by CommonDeviceActions {
    override val scanAction get() = CommsProtocol.Action(ContextProvider.appContext.getString(R.string.scan))
    override val disconnectAction get() = CommsProtocol.Action(ContextProvider.appContext.getString(R.string.menu_disconnect))
    override val sniffAction get() = CommsProtocol.Action(ContextProvider.appContext.getString(R.string.scanblercprotocol_sniff))
}