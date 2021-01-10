package com.tunjid.rcswitchcontrol.models

import android.content.Context
import android.content.res.Resources
import androidx.fragment.app.Fragment
import com.jakewharton.rx.replayingShare
import com.rcswitchcontrol.protocols.CommonDeviceActions
import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.Name
import com.rcswitchcontrol.protocols.models.Payload
import com.rcswitchcontrol.zigbee.models.ZigBeeAttribute
import com.rcswitchcontrol.zigbee.models.ZigBeeCommandInfo
import com.rcswitchcontrol.zigbee.models.ZigBeeNode
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.a433mhz.models.RfSwitch
import com.tunjid.rcswitchcontrol.a433mhz.protocols.BLERFProtocol
import com.tunjid.rcswitchcontrol.a433mhz.protocols.SerialRFProtocol
import com.tunjid.rcswitchcontrol.a433mhz.services.ClientBleService
import com.tunjid.rcswitchcontrol.common.deserialize
import com.tunjid.rcswitchcontrol.common.deserializeList
import com.tunjid.rcswitchcontrol.fragments.DevicesFragment
import com.tunjid.rcswitchcontrol.fragments.HostFragment
import com.tunjid.rcswitchcontrol.fragments.RecordFragment
import com.tunjid.rcswitchcontrol.utils.Tab
import io.reactivex.Flowable
import io.reactivex.rxkotlin.Flowables
import io.reactivex.schedulers.Schedulers
import java.util.HashMap
import java.util.Locale

data class ProtocolKey(val key: CommsProtocol.Key) : Tab {
    val title get() = key.value.split(".").last().toUpperCase(Locale.US).removeSuffix("PROTOCOL")

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

data class ControlState(
    val isNew: Boolean = false,
    val connectionState: String = "",
    val commandInfo: ZigBeeCommandInfo? = null,
    val history: List<Record> = listOf(),
    val commands: Map<CommsProtocol.Key, List<Record.Command>> = mapOf(),
    val devices: List<Device> = listOf()
)

fun controlState(
    status: Flowable<String>,
    payloads: Flowable<Payload>
) = Flowables.combineLatest(
    status,
    payloads
).scan(ControlState()) { state, (connectionState, payload) ->
    val key = payload.key
    val isNew = !state.commands.keys.contains(key)

    state.copy(isNew = isNew, connectionState = connectionState, commandInfo = payload.extractCommandInfo)
        .reduceCommands(payload)
        .reduceDeviceName(payload.deviceName)
        .reduceHistory(payload.extractRecord)
        .reduceDevices(payload.extractDevices)
        .reduceZigBeeAttributes(payload.extractDeviceAttributes)
}
    .subscribeOn(Schedulers.single())
    .replayingShare()

val ControlState.keys
    get() = commands.keys
        .sortedBy(CommsProtocol.Key::value)
        .map(::ProtocolKey)

private fun ControlState.reduceDevices(fetched: List<Device>?) = when {
    fetched != null -> copy(devices = (fetched + devices)
        .distinctBy(Device::diffId)
        .sortedBy(Device::name))
    else -> this
}

private fun ControlState.reduceZigBeeAttributes(fetched: List<ZigBeeAttribute>?) = when (fetched) {
    null -> this
    else -> copy(devices = devices
        .filterIsInstance<Device.ZigBee>()
        .map { it.foldAttributes(fetched) }
        .plus(devices)
        .distinctBy(Device::diffId)
    )
}

private fun ControlState.reduceHistory(record: Record?) = when {
    record != null -> copy(history = (history + record).takeLast(500))
    else -> this
}

private fun ControlState.reduceCommands(payload: Payload) = copy(
    commands = HashMap(commands).apply {
        this[payload.key] = payload.commands.map { Record.Command(key = payload.key, command = it) }
    }
)

private fun ControlState.reduceDeviceName(name: Name?) = when (name) {
    null -> this
    else -> copy(devices = listOfNotNull(devices.firstOrNull { it.id == name.id }.let {
        when (it) {
            is Device.RF -> it.copy(switch = it.switch.copy(name = name.value))
            is Device.ZigBee -> it.copy(givenName = name.value)
            else -> it
        }
    })
        .plus(devices)
        .distinctBy(Device::diffId)
    )
}

private val Payload.deviceName: Name?
    get() = when (action) {
        CommonDeviceActions.nameChangedAction -> data?.deserialize(Name::class)
        else -> null
    }
private val Payload.extractRecord: Record.Response?
    get() = response.let {
        if (it == null || it.isBlank()) null
        else Record.Response(key = key, entry = it)
    }

private val Payload.extractCommandInfo: ZigBeeCommandInfo?
    get() = if (key == ZigBeeProtocol.key && action == ZigBeeProtocol.commandInfoAction) data?.deserialize(ZigBeeCommandInfo::class)
    else null

private val Payload.extractDevices: List<Device>?
    get() = when (val serialized = data) {
        null -> null
        else -> when (key) {
            BLERFProtocol.key, SerialRFProtocol.key -> when (action) {
                ClientBleService.transmitterAction,
                CommonDeviceActions.deleteAction,
                CommonDeviceActions.renameAction -> serialized.deserializeList(RfSwitch::class)
                    .map(Device::RF)
                else -> null
            }
            ZigBeeProtocol.key -> when (action) {
                CommonDeviceActions.refreshDevicesAction -> serialized.deserializeList(ZigBeeNode::class)
                    .map(Device::ZigBee)
                else -> null
            }
            else -> null
        }
    }

private val Payload.extractDeviceAttributes: List<ZigBeeAttribute>?
    get() = when (key) {
        ZigBeeProtocol.key -> when (action) {
            in ZigBeeProtocol.attributeCarryingActions -> data?.deserializeList(ZigBeeAttribute::class)
            else -> null
        }
        else -> null
    }