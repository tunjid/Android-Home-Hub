package com.rcswitchcontrol.zigbee.models

import com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster
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
        OnOff(ZclOnOffCluster.ATTR_ONOFF),
        Level(ZclLevelControlCluster.ATTR_CURRENTLEVEL),
        Hue(ZclColorControlCluster.ATTR_CURRENTHUE),
        Saturation(ZclColorControlCluster.ATTR_CURRENTSATURATION),
        CieX(ZclColorControlCluster.ATTR_CURRENTX),
        CieY(ZclColorControlCluster.ATTR_CURRENTY),
    }
}

val ZigBeeAttribute.distinctId get() = "$endpointId-$clusterId-$attributeId"

fun List<ZigBeeAttribute>.of(descriptor: ZigBeeAttribute.Descriptor) = firstOrNull { it.attributeId == descriptor.id }

val ZigBeeAttribute.numValue get() = value as? Number