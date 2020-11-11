package com.rcswitchcontrol.zigbee.models

data class ZigBeeAttribute(
        val id: Int,
        val endpointId: Int,
        val clusterId: Int,
        val type: String,
        val value: Any
)

val ZigBeeAttribute.distinctId get() = "$endpointId-$clusterId-$id"

fun ZigBeeAttribute.supports(feature: ZigBeeNode.Feature) = clusterId == feature.clusterType.id