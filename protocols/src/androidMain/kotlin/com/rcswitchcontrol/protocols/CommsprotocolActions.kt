package com.rcswitchcontrol.protocols

interface DeviceActions {
    val renameAction: CommsProtocol.Action
    val nameChangedAction: CommsProtocol.Action
    val deleteAction: CommsProtocol.Action
    val refreshDevicesAction: CommsProtocol.Action
}

object CommonDeviceActions: DeviceActions {
    override val renameAction get() = CommsProtocol.Action("Rename")
    override val nameChangedAction get() = CommsProtocol.Action("Name Changed")
    override val deleteAction get() = CommsProtocol.Action("Delete")
    override val refreshDevicesAction get() = CommsProtocol.Action("Refresh Devices")
}