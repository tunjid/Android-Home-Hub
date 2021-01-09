/*
 * MIT License
 *
 * Copyright (c) 2019 Adetunji Dahunsi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.rcswitchcontrol.zigbee.models

import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.models.Peripheral
import com.rcswitchcontrol.zigbee.R
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol
import com.zsmartsystems.zigbee.database.ZclAttributeDao
import com.zsmartsystems.zigbee.database.ZclClusterDao
import com.zsmartsystems.zigbee.database.ZigBeeNodeDao
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType

data class ZigBeeNode internal constructor(
        override val id: String,
        override val key: CommsProtocol.Key = ZigBeeProtocol.key,
        @Transient // This is not serialized, it's chunky
        internal val node: ZigBeeNodeDao
) : Peripheral {

    enum class Feature(
            val nameRes: Int,
            internal val clusterType: ZclClusterType,
            internal val descriptors: List<ZigBeeAttribute.Descriptor>
    ) {
        OnOff(
                nameRes = R.string.zigbee_feature_on_off,
                clusterType = ZclClusterType.ON_OFF,
                descriptors = listOf(ZigBeeAttribute.Descriptor.OnOff)
        ),
        Level(
                nameRes = R.string.zigbee_feature_level,
                clusterType = ZclClusterType.LEVEL_CONTROL,
                descriptors = listOf(ZigBeeAttribute.Descriptor.Level)
        ),
        Color(
                nameRes = R.string.zigbee_feature_color,
                clusterType = ZclClusterType.COLOR_CONTROL,
                descriptors = listOf(ZigBeeAttribute.Descriptor.CieX, ZigBeeAttribute.Descriptor.CieY)
        ),
    }

    override val diffId
        get() = ieeeAddress

    // ******** WIRE SERIALIZED ******** //
    internal val ieeeAddress: String = node.ieeeAddress.toString()
    internal val networkAdress: String = node.networkAddress.toString()

    internal val endpointClusterMap: Map<Int, Set<Int>> = node.endpoints
            .map { it.endpointId to it.inputClusters.map(ZclClusterDao::getClusterId).toSet() }
            .toMap()

    internal val clusterAttributeMap: Map<Int, Set<Int>> = node.endpoints
            .map { endpoint -> endpoint.inputClusters.map { it.clusterId to it.attributes.values.map(ZclAttributeDao::getId).toSet() } }
            .flatten()
            .toMap()
    // ******** WIRE SERIALIZED ******** //

    val supportedFeatures
        get() = Feature.values().filter(::supports)

    fun supports(feature: Feature) = supports(feature.clusterType)

    internal fun address(clusterType: ZclClusterType) = "$networkAdress/${endpointId(clusterType)}"

    private fun supports(clusterType: ZclClusterType) = address(clusterType).split("/").getOrNull(1) != "null"

    private fun endpointId(clusterType: ZclClusterType) = endpointClusterMap
            .entries
            .firstOrNull { it.value.contains(clusterType.id) }
            ?.key

    fun command(input: ZigBeeInput<*>): ZigBeeCommand = input.commandFor(this)

    override fun hashCode(): Int = ieeeAddress.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ZigBeeNode

        if (id != other.id) return false
        if (ieeeAddress != other.ieeeAddress) return false
        if (networkAdress != other.networkAdress) return false
        if (endpointClusterMap != other.endpointClusterMap) return false
        if (clusterAttributeMap != other.clusterAttributeMap) return false

        return true
    }
}

//fun List<ZigBeeNode>.createGroupSequence(groupName: String): List<ZigBeeCommand> {
//    val result = mutableListOf<ZigBeeCommand>()
//
//    val groupId = groupName.hashCode().toString()
//
//    result.add(GroupAddCommand().command.let { ZigBeeCommand(it, listOf(it, groupId, groupName)) })
//    result.addAll(map { device ->
//        MembershipAddCommand().command.let { ZigBeeCommand(it, listOf(it, device.address(ZclClusterType.ON_OFF), groupId, groupName)) }
//    })
//
//    return result
//}

internal fun ZigBeeNodeDao.device(): ZigBeeNode = ZigBeeNode(
        id = ieeeAddress.toString(),
        node = this
)

fun ZigBeeNode.owns(attribute: ZigBeeAttribute) =
        attribute.nodeAddress == ZclClusterType.values()
                .firstOrNull { it.id == attribute.clusterId }
                ?.let(this::address)
                && endpointClusterMap.keys.contains(attribute.endpointId)