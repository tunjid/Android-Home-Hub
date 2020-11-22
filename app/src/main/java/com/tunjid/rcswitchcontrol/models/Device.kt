package com.tunjid.rcswitchcontrol.models

import androidx.core.graphics.ColorUtils
import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.models.Payload
import com.rcswitchcontrol.zigbee.models.ZigBeeAttribute
import com.rcswitchcontrol.zigbee.models.ZigBeeNode
import com.rcswitchcontrol.zigbee.models.distinctId
import com.rcswitchcontrol.zigbee.models.matches
import com.rcswitchcontrol.zigbee.models.numValue
import com.rcswitchcontrol.zigbee.models.owns
import com.tunjid.androidx.recyclerview.diff.Differentiable
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.a433mhz.models.RfSwitch
import com.tunjid.rcswitchcontrol.a433mhz.services.ClientBleService
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.tunjid.rcswitchcontrol.common.serialize

sealed class Device(
        val key: CommsProtocol.Key,
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
            "isOn" to isOn,
            "level" to level,
            "color" to color
    )

val Device.ZigBee.isOn
    get() = describe(ZigBeeAttribute.Descriptor.OnOff)?.value as? Boolean

val Device.ZigBee.level
    get() = when (val value = describe(ZigBeeAttribute.Descriptor.Level)?.numValue) {
        is Number -> value.toFloat() * 100f / 256
        else -> null
    }

val Device.ZigBee.color
    get() = listOf(ZigBeeAttribute.Descriptor.CieX, ZigBeeAttribute.Descriptor.CieY)
            .mapNotNull(::describe)
            .mapNotNull(ZigBeeAttribute::numValue)
            .map(Number::toDouble)
            .map { it / 65535 }
            .let {
                when {
                    it.size < 2 -> null
                    else -> {
                        val (x, y) = it
                        @Suppress("LocalVariableName") val X = x / y
                        @Suppress("LocalVariableName") val Y = y
                        @Suppress("LocalVariableName") val Z = Y * (1 - x - y) / y

                        ColorUtils.XYZToColor(X, Y, Z)
                    }
                }
            }

fun Device.ZigBee.describe(descriptor: ZigBeeAttribute.Descriptor) =
        attributes.firstOrNull(descriptor::matches)

fun Device.ZigBee.foldAttributes(attributes: List<ZigBeeAttribute>): Device.ZigBee =
        attributes.fold(this) { device, attribute ->
            if (device.node.owns(attribute)) device.copy(attributes = listOf(attribute)
                    .plus(device.attributes)
                    .distinctBy(ZigBeeAttribute::distinctId)
            )
            else device
        }