package com.tunjid.rcswitchcontrol.models

import com.rcswitchcontrol.zigbee.models.ZigBeeAttribute
import com.rcswitchcontrol.zigbee.models.ZigBeeNode
import com.rcswitchcontrol.zigbee.models.distinctId
import com.rcswitchcontrol.zigbee.models.owns
import com.rcswitchcontrol.zigbee.models.valueOf
import com.tunjid.androidx.recyclerview.diff.Differentiable
import com.tunjid.rcswitchcontrol.a433mhz.models.RfSwitch

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

val Device.ZigBee.trifecta
    get() = Triple(
            "level" to level,
            "color" to color,
            "isOn" to isOn
    )

val Device.ZigBee.isOn
    get() = attributes.valueOf(ZigBeeAttribute.Descriptor.OnOffState) as? Boolean

val Device.ZigBee.level
    get() = attributes.valueOf(ZigBeeAttribute.Descriptor.LevelState)


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