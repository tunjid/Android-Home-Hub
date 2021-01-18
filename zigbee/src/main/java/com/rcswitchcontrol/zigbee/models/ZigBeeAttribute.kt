package com.rcswitchcontrol.zigbee.models

import com.tunjid.rcswitchcontrol.common.Writable
import com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster

@kotlinx.serialization.Serializable
sealed class Value : Writable {
    @kotlinx.serialization.Serializable
    data class Int(val item: kotlin.Int) : Value()
    @kotlinx.serialization.Serializable
    data class Float(val item: kotlin.Float) : Value()
    @kotlinx.serialization.Serializable
    data class Boolean(val item: kotlin.Boolean) : Value()
}

val Value.item: Any
    get() = when (this) {
        is Value.Int -> item
        is Value.Float -> item
        is Value.Boolean -> item
    }

val Value.number: Number?
    get() = when (this) {
        is Value.Int -> item
        is Value.Float -> item
        is Value.Boolean -> null
    }

@kotlinx.serialization.Serializable
data class ZigBeeAttribute(
    val attributeId: Int,
    val nodeAddress: String,
    val endpointId: Int,
    val clusterId: Int,
    val type: String,
    val value: Value
) : Writable {
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

fun ZigBeeAttribute.Descriptor.matches(attribute: ZigBeeAttribute) =
    attribute.attributeId == attributeId && attribute.clusterId == clusterId