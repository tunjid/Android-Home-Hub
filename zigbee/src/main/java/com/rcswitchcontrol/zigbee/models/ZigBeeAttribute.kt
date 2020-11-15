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
    enum class Descriptor(
            internal val attributeId: Int,
            internal val clusterId: Int
    ) {
        OnOff(ZclOnOffCluster.ATTR_ONOFF, ZclOnOffCluster.CLUSTER_ID),
        Level(ZclLevelControlCluster.ATTR_CURRENTLEVEL, ZclLevelControlCluster.CLUSTER_ID),
        Hue(ZclColorControlCluster.ATTR_CURRENTHUE, ZclColorControlCluster.CLUSTER_ID),
        Saturation(ZclColorControlCluster.ATTR_CURRENTSATURATION, ZclColorControlCluster.CLUSTER_ID),
        CieX(ZclColorControlCluster.ATTR_CURRENTX, ZclColorControlCluster.CLUSTER_ID),
        CieY(ZclColorControlCluster.ATTR_CURRENTY, ZclColorControlCluster.CLUSTER_ID),
    }
}

val ZigBeeAttribute.distinctId get() = "$endpointId-$clusterId-$attributeId"

val ZigBeeAttribute.numValue get() = value as? Number

fun ZigBeeAttribute.Descriptor.matches(attribute: ZigBeeAttribute) =
        attribute.attributeId == attributeId && attribute.clusterId == clusterId