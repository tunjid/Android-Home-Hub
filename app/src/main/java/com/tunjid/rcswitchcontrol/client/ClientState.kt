package com.tunjid.rcswitchcontrol.client

import android.net.nsd.NsdServiceInfo
import android.os.Parcelable
import com.rcswitchcontrol.protocols.CommonDeviceActions
import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.Name
import com.rcswitchcontrol.protocols.models.Payload
import com.rcswitchcontrol.zigbee.models.ZigBeeAttribute
import com.rcswitchcontrol.zigbee.models.ZigBeeCommandInfo
import com.rcswitchcontrol.zigbee.models.ZigBeeNode
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol
import com.tunjid.rcswitchcontrol.a433mhz.models.RfSwitch
import com.tunjid.rcswitchcontrol.a433mhz.protocols.BLERFProtocol
import com.tunjid.rcswitchcontrol.a433mhz.protocols.SerialRFProtocol
import com.tunjid.rcswitchcontrol.a433mhz.services.ClientBleService
import com.tunjid.rcswitchcontrol.common.Writable
import com.tunjid.rcswitchcontrol.common.deserialize
import com.tunjid.rcswitchcontrol.common.deserializeList
import com.tunjid.rcswitchcontrol.common.serialize
import com.tunjid.rcswitchcontrol.control.Device
import com.tunjid.rcswitchcontrol.control.Record
import com.tunjid.rcswitchcontrol.control.foldAttributes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.parcelize.Parcelize
import java.util.*

val CommsProtocol.Key.name get() = value
    .split(".")
    .last()
    .toUpperCase(Locale.US)
    .removeSuffix("PROTOCOL")

data class ProtocolKey(
    val key: CommsProtocol.Key
) : CharSequence by key.name

sealed class ClientLoad : Parcelable {
    @Parcelize
    data class NewClient(val info: NsdServiceInfo) : ClientLoad()

    @Parcelize
    data class ExistingClient(val serviceName: String) : ClientLoad()

    @Parcelize
    object StartServer : ClientLoad()
}

@kotlinx.serialization.Serializable
data class ClientState(
    val isNew: Boolean = false,
    val connectionStatus: Status = Status.Disconnected(),
    val commandInfo: ZigBeeCommandInfo? = null,
    val history: List<Record> = listOf(),
    val commands: Map<CommsProtocol.Key, List<Record.Command>> = mapOf(),
    val devices: List<Device> = listOf()
) : Writable

val ClientState?.isConnected get() = this?.connectionStatus is Status.Connected

private val cacheAction = CommsProtocol.Action("controlStateCache")

fun CoroutineScope.clientState(
    status: Flow<Status>,
    payloads: Flow<Payload>
): Flow<ClientState> = combine(
    status,
    payloads,
    ::Pair,
).scan(ClientState(), ClientState::reduce)
    .flowOn(Dispatchers.IO)
    .shareIn(scope = this, started = SharingStarted.WhileSubscribed(), replay = 1)

class ClientStateCache {
    private var cache = ClientState()

    val payload
        get() = Payload(
            key = CommsProtocol.key,
            action = cacheAction,
            data = ClientState(devices = cache.devices).serialize()
        )

    fun add(input: String) {
        cache = cache.reduce(Status.Disconnected() to input.deserialize())
    }

    override fun toString(): String = "ControlStateCache:$cache"
}

val ClientState.keys
    get() = commands.keys
        .sortedBy(CommsProtocol.Key::value)
        .map(::ProtocolKey)

private fun ClientState.reduce(pair: Pair<Status, Payload>): ClientState {
    val (connectionStatus, payload) = pair
    val key = payload.key
    val isNew = !commands.keys.contains(key)

    return payload.cache ?: copy(
        isNew = isNew,
        connectionStatus = connectionStatus,
        commandInfo = payload.extractCommandInfo
    )
        .reduceCommands(payload)
        .reduceDeviceName(payload.deviceName)
        .reduceHistory(payload.extractRecord)
        .reduceDevices(payload.extractDevices)
        .reduceZigBeeAttributes(payload.extractDeviceAttributes)
}

private fun ClientState.reduceDevices(fetched: List<Device>?) = when {
    fetched != null -> copy(
        devices = (fetched + devices)
            .distinctBy(Device::diffId)
            .sortedBy(Device::name)
    )
    else -> this
}

private fun ClientState.reduceZigBeeAttributes(fetched: List<ZigBeeAttribute>?) = when (fetched) {
    null -> this
    else -> copy(devices = devices
        .filterIsInstance<Device.ZigBee>()
        .map { it.foldAttributes(fetched) }
        .plus(devices)
        .distinctBy(Device::diffId)
    )
}

private fun ClientState.reduceHistory(record: Record?) = when {
    record != null -> copy(history = (history + record).takeLast(500))
    else -> this
}

private fun ClientState.reduceCommands(payload: Payload) = copy(
    commands = HashMap(commands).apply {
        this[payload.key] = payload.commands.map { Record.Command(key = payload.key, command = it) }
    }
)

private fun ClientState.reduceDeviceName(name: Name?) = when (name) {
    null -> this
    else -> copy(
        devices = listOfNotNull(devices.firstOrNull { it.id == name.id }.let {
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

private val Payload.cache: ClientState?
    get() = when (action) {
        cacheAction -> data?.deserialize()
        else -> null
    }

private val Payload.deviceName: Name?
    get() = when (action) {
        CommonDeviceActions.nameChangedAction -> data?.deserialize()
        else -> null
    }

private val Payload.extractRecord: Record.Response?
    get() = response.let {
        if (it == null || it.isBlank()) null
        else Record.Response(key = key, entry = it)
    }

private val Payload.extractCommandInfo: ZigBeeCommandInfo?
    get() = if (key == ZigBeeProtocol.key && action == ZigBeeProtocol.commandInfoAction) data?.deserialize()
    else null

private val Payload.extractDevices: List<Device>?
    get() = when (val serialized = data) {
        null, "" -> null
        else -> when (key) {
            BLERFProtocol.key, SerialRFProtocol.key -> when (action) {
                ClientBleService.transmitterAction,
                CommonDeviceActions.deleteAction,
                CommonDeviceActions.renameAction -> serialized.deserializeList<RfSwitch>()
                    .map(Device::RF)
                else -> null
            }
            ZigBeeProtocol.key -> when (action) {
                CommonDeviceActions.refreshDevicesAction -> serialized.deserializeList<ZigBeeNode>()
                    .map(Device::ZigBee)
                else -> null
            }
            else -> null
        }
    }

private val Payload.extractDeviceAttributes: List<ZigBeeAttribute>?
    get() = when (key) {
        ZigBeeProtocol.key -> when (action) {
            in ZigBeeProtocol.attributeCarryingActions -> data?.deserializeList()
            else -> null
        }
        else -> null
    }
