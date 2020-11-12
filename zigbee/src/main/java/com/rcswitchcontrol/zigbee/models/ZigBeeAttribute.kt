package com.rcswitchcontrol.zigbee.models

import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster

data class ZigBeeAttribute(
        val attributeId: Int,
        val nodeAddress:String,
        val endpointId: Int,
        val clusterId: Int,
        val type: String,
        val value: Any
) {
    enum class Descriptor(val id: Int) {
        OnOffState(ZclOnOffCluster.ATTR_ONOFF),
        LevelState(ZclLevelControlCluster.ATTR_CURRENTLEVEL)
    }
}

val ZigBeeAttribute.distinctId get() = "$endpointId-$clusterId-$attributeId"

fun List<ZigBeeAttribute>.valueOf(descriptor: ZigBeeAttribute.Descriptor) = firstOrNull { it.attributeId == descriptor.id }?.value