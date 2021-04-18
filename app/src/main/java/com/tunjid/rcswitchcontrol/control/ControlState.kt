package com.tunjid.rcswitchcontrol.control

import android.content.res.Resources
import androidx.fragment.app.Fragment
import com.rcswitchcontrol.protocols.models.Payload
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.client.ClientLoad
import com.tunjid.rcswitchcontrol.client.ClientState
import com.tunjid.rcswitchcontrol.server.HostFragment
import com.tunjid.rcswitchcontrol.utils.Tab
import io.reactivex.Flowable

typealias ClientServiceState = com.tunjid.rcswitchcontrol.client.State

sealed class Input {
    sealed class Sync : Input() {
        data class Select(val device: Device) : Sync()
        object ClearSelections : Sync()
    }

    sealed class Async: Input() {
        data class Load(val load: ClientLoad) : Async()
        data class ServerCommand(val payload: Payload) : Async()
        data class ClientServiceBound(val clientServiceState: Flowable<ClientServiceState>) : Async()
        object ForgetServer : Async()
        object PingServer : Async()
        object AppBackgrounded : Async()
    }
}

data class ControlState(
    val clientState: ClientState = ClientState(),
    val selectedDevices: List<Device> = listOf(),
)

enum class Page : Tab {

    HOST, HISTORY, DEVICES;

    override fun createFragment(): Fragment = when (this) {
        HOST -> HostFragment.newInstance()
        HISTORY -> RecordFragment.historyInstance()
        DEVICES -> DevicesFragment.newInstance()
    }

    override fun title(res: Resources): CharSequence = when (this) {
        HOST -> res.getString(R.string.host)
        HISTORY -> res.getString(R.string.history)
        DEVICES -> res.getString(R.string.devices)
    }
}