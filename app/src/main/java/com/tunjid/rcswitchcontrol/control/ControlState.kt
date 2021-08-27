package com.tunjid.rcswitchcontrol.control

import com.rcswitchcontrol.protocols.models.Payload
import com.tunjid.rcswitchcontrol.client.ClientLoad
import com.tunjid.rcswitchcontrol.client.ClientState
import kotlinx.coroutines.flow.Flow

typealias ClientServiceState = com.tunjid.rcswitchcontrol.client.State

sealed class Input {
    sealed class Sync : Input() {
        data class Select(val device: Device) : Sync()
        object ClearSelections : Sync()
    }

    sealed class Async: Input() {
        data class Load(val load: ClientLoad) : Async()
        data class ServerCommand(val payload: Payload) : Async()
        data class ClientServiceBound(val clientServiceState: Flow<ClientServiceState>) : Async()
        object ForgetServer : Async()
        object PingServer : Async()
        object AppBackgrounded : Async()
    }
}

data class ControlState(
    val clientState: ClientState = ClientState(),
    val selectedDevices: List<Device> = listOf(),
)

enum class Page: CharSequence {

    Host, History, Devices;

    override val length: Int
        get() = name.length

    override fun get(index: Int): Char =
        name.get(index = index)

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        name.subSequence(startIndex, endIndex)
//    }
}