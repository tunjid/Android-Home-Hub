package com.rcswitchcontrol.protocols

import com.tunjid.rcswitchcontrol.common.ContextProvider

interface DeviceActions {
    val renameAction: CommsProtocol.Action
    val deleteAction: CommsProtocol.Action
    val refreshDevicesAction: CommsProtocol.Action
}

object CommonDeviceActions: DeviceActions {
    override val renameAction get() = CommsProtocol.Action(ContextProvider.appContext.getString(R.string.device_action_rename_command))
    override val deleteAction get() = CommsProtocol.Action(ContextProvider.appContext.getString(R.string.device_action_delete_command))
    override val refreshDevicesAction get() = CommsProtocol.Action(ContextProvider.appContext.getString(R.string.device_action_refresh_devices_command))
}