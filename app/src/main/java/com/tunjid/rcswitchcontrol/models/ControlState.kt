package com.tunjid.rcswitchcontrol.models

import android.content.res.Resources
import androidx.fragment.app.Fragment
import com.rcswitchcontrol.protocols.models.Payload
import com.rcswitchcontrol.zigbee.models.ZigBeeCommandInfo
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.fragments.DevicesFragment
import com.tunjid.rcswitchcontrol.fragments.HostFragment
import com.tunjid.rcswitchcontrol.fragments.RecordFragment
import com.tunjid.rcswitchcontrol.utils.Tab
import java.util.*

data class ControlState(
        val isNew: Boolean = false,
        val connectionState: String = "",
        val commandInfo: ZigBeeCommandInfo? = null,
        val history: List<Record> = listOf(),
        val commands: Map<String, List<Record>> = mapOf(),
        val devices: List<Device> = listOf()
)

val ControlState.keys get() = commands.keys.sorted().map(::ProtocolKey)
fun ControlState.reduceDevices(fetched: List<Device>?) = when {
    fetched != null -> copy(devices = (fetched + devices)
            .distinctBy(Device::diffId)
            .sortedBy(Device::name))
    else -> this
}

fun ControlState.reduceHistory(record: Record?) = when {
    record != null -> copy(history = (history + record))
    else -> this
}

fun ControlState.reduceCommands(payload: Payload) = copy(
        commands = HashMap(commands).apply {
            this[payload.key] = payload.commands.map { Record(payload.key, it, true) }
        }
)

data class ProtocolKey(val name: String) : Tab {
    val title get() = name.split(".").last().toUpperCase(Locale.US).removeSuffix("PROTOCOL")

    override fun title(res: Resources) = title

    override fun createFragment(): Fragment = RecordFragment.commandInstance(this)
}

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