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

import com.rcswitchcontrol.protocols.models.Peripheral
import com.rcswitchcontrol.zigbee.R
import com.rcswitchcontrol.zigbee.commands.GroupAddCommand
import com.rcswitchcontrol.zigbee.commands.MembershipAddCommand
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.zsmartsystems.zigbee.database.ZclAttributeDao
import com.zsmartsystems.zigbee.database.ZclClusterDao
import com.zsmartsystems.zigbee.database.ZigBeeNodeDao
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType

data class ZigBeeAttribute(
        val id: Int,
        val endpointId: Int,
        val clusterId: Int,
        val type: String,
        val value: Any
)

data class ZigBeeNode internal constructor(
        override val name: String,
        override val key: String = ZigBeeProtocol::class.java.name,

        @Transient // This is not serialized, it's chunky
        internal val node: ZigBeeNodeDao
) : Peripheral {

    enum class Feature(
            val nameRes: Int,
            internal val clusterType: ZclClusterType
    ) {
        OnOff(R.string.zigbee_feature_on_off, ZclClusterType.ON_OFF),
        Color(R.string.zigbee_feature_color, ZclClusterType.COLOR_CONTROL),
        Level(R.string.zigbee_feature_level, ZclClusterType.LEVEL_CONTROL),
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

    fun command(input: ZigBeeInput<*>): ZigBeeCommand = input.from(this)

    override fun hashCode(): Int = ieeeAddress.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ZigBeeNode

        if (name != other.name) return false
        if (ieeeAddress != other.ieeeAddress) return false
        if (networkAdress != other.networkAdress) return false
        if (endpointClusterMap != other.endpointClusterMap) return false
        if (clusterAttributeMap != other.clusterAttributeMap) return false

        return true
    }

    companion object {
        val SAVED_DEVICES_ACTION get() = ContextProvider.appContext.getString(R.string.zigbeeprotocol_saved_devices)
        val DEVICE_ATTRIBUTES_ACTION get() = ContextProvider.appContext.getString(R.string.zigbeeprotocol_device_attributes)
    }
}

fun List<ZigBeeNode>.createGroupSequence(groupName: String): List<ZigBeeCommand> {
    val result = mutableListOf<ZigBeeCommand>()

    val groupId = groupName.hashCode().toString()

    result.add(GroupAddCommand().command.let { ZigBeeCommand(it, listOf(it, groupId, groupName)) })
    result.addAll(map { device ->
        MembershipAddCommand().command.let { ZigBeeCommand(it, listOf(it, device.address(ZclClusterType.ON_OFF), groupId, groupName)) }
    })

    return result
}

internal fun ZigBeeNodeDao.device(): ZigBeeNode? = ZigBeeNode(
        name = ieeeAddress.toString(),
        node = this
)
