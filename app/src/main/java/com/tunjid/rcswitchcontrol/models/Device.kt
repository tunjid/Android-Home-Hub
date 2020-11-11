package com.tunjid.rcswitchcontrol.models

import com.rcswitchcontrol.zigbee.models.ZigBeeAttribute
import com.rcswitchcontrol.zigbee.models.ZigBeeNode
import com.tunjid.androidx.recyclerview.diff.Differentiable
import com.tunjid.rcswitchcontrol.a433mhz.models.RfSwitch

sealed class Device(
        val key: String,
        val name: String
) : Differentiable {
    data class ZigBee(
            val node: ZigBeeNode,
            val attributes: List<ZigBeeAttribute> = listOf()
    ) : Device(node.key, node.name), Differentiable by node
    data class RF(
            val switch: RfSwitch
    ) : Device(switch.key, switch.name), Differentiable by switch
}