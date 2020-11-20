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
        OnOff(
                attributeId = ZclOnOffCluster.ATTR_ONOFF,
                clusterId = ZclOnOffCluster.CLUSTER_ID
        ),
        Level(
                attributeId = ZclLevelControlCluster.ATTR_CURRENTLEVEL,
                clusterId = ZclLevelControlCluster.CLUSTER_ID
        ),
        Hue(
                attributeId = ZclColorControlCluster.ATTR_CURRENTHUE,
                clusterId = ZclColorControlCluster.CLUSTER_ID
        ),
        Saturation(
                attributeId = ZclColorControlCluster.ATTR_CURRENTSATURATION,
                clusterId = ZclColorControlCluster.CLUSTER_ID
        ),
        CieX(
                attributeId = ZclColorControlCluster.ATTR_CURRENTX,
                clusterId = ZclColorControlCluster.CLUSTER_ID
        ),
        CieY(
                attributeId = ZclColorControlCluster.ATTR_CURRENTY,
                clusterId = ZclColorControlCluster.CLUSTER_ID
        ),
    }
}

val ZigBeeAttribute.distinctId get() = "$endpointId-$clusterId-$attributeId"

val ZigBeeAttribute.numValue get() = value as? Number

fun ZigBeeAttribute.Descriptor.matches(attribute: ZigBeeAttribute) =
        attribute.attributeId == attributeId && attribute.clusterId == clusterId