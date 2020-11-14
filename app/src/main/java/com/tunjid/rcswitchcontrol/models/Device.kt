package com.tunjid.rcswitchcontrol.models

import android.util.Log
import com.rcswitchcontrol.protocols.models.Payload
import com.rcswitchcontrol.zigbee.models.ZigBeeAttribute
import com.rcswitchcontrol.zigbee.models.ZigBeeNode
import com.rcswitchcontrol.zigbee.models.distinctId
import com.rcswitchcontrol.zigbee.models.owns
import com.rcswitchcontrol.zigbee.models.valueOf
import com.tunjid.androidx.recyclerview.diff.Differentiable
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.a433mhz.models.RfSwitch
import com.tunjid.rcswitchcontrol.a433mhz.services.ClientBleService
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.tunjid.rcswitchcontrol.common.serialize

sealed class Device(
        val key: String,
        val name: String
) : Differentiable {
    data class ZigBee(
            val node: ZigBeeNode,
            val attributes: List<ZigBeeAttribute> = listOf()
    ) : Device(node.key, node.name), Differentiable by node {
        override fun areContentsTheSame(other: Differentiable): Boolean = this == other
    }

    data class RF(
            val switch: RfSwitch
    ) : Device(switch.key, switch.name), Differentiable by switch {
        override fun areContentsTheSame(other: Differentiable): Boolean = this == other
    }
}

val Device.RF.deletePayload
    get() = Payload(
            key = key,
            action = ContextProvider.appContext.getString(R.string.blercprotocol_delete_command),
            data = serialize()

    )

val Device.RF.renamedPayload
    get() = Payload(
            key = key,
            action = ContextProvider.appContext.getString(R.string.blercprotocol_rename_command),
            data = serialize()

    )

fun Device.RF.togglePayload(isOn: Boolean) = Payload(
        key = key,
        action = ClientBleService.ACTION_TRANSMITTER,
        data = switch.getEncodedTransmission(isOn)
)

val Device.ZigBee.trifecta
    get() = Triple(
            "level" to level,
            "color" to color,
            "isOn" to isOn
    )

val Device.ZigBee.isOn
    get() = attributes.valueOf(ZigBeeAttribute.Descriptor.OnOffState) as? Boolean

val Device.ZigBee.level
    get() = when (val value = attributes.valueOf(ZigBeeAttribute.Descriptor.LevelState)) {
        is Float -> value * 100f / 256
        is Double -> value.toFloat() * 100f / 256
        is Int -> value.toFloat() * 100f / 256
        else -> null
    }.also { if (it != null) Log.i("TEST", "Level of $name is $it") }


val Device.ZigBee.color
    get() = attributes.valueOf(ZigBeeAttribute.Descriptor.LevelState)


fun Device.ZigBee.foldAttributes(attributes: List<ZigBeeAttribute>): Device.ZigBee =
        attributes.fold(this) { device, attribute ->
            if (device.node.owns(attribute)) device.copy(attributes = listOf(attribute)
                    .plus(device.attributes)
                    .distinctBy(ZigBeeAttribute::distinctId)
            )
            else device
        }