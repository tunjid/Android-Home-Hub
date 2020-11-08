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

import android.util.Log
import com.rcswitchcontrol.protocols.models.Device
import com.rcswitchcontrol.zigbee.commands.GroupAddCommand
import com.rcswitchcontrol.zigbee.commands.MembershipAddCommand
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol
import com.tunjid.rcswitchcontrol.common.serialize
import com.zsmartsystems.zigbee.database.ZigBeeEndpointDao
import com.zsmartsystems.zigbee.database.ZigBeeNodeDao
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType

data class ZigBeeDevice internal constructor(
        override val name: String,
        override val key: String = ZigBeeProtocol::class.java.name,

        @Transient // This is not serialized, it's chunky
        internal val node: ZigBeeNodeDao,

        // The following need to be serialized over the wire to send commands back

        internal val ieeeAddress: String = node.ieeeAddress.toString(),
        internal val networkAdress: String = node.networkAddress.toString(),

        internal val onOffAddress: String = "$networkAdress/${node.endpointFor(ZclClusterType.ON_OFF)?.endpointId}",
        internal val levelAddress: String = "$networkAdress/${node.endpointFor(ZclClusterType.LEVEL_CONTROL)?.endpointId}",
        internal val colorAddress: String = "$networkAdress/${node.endpointFor(ZclClusterType.COLOR_CONTROL)?.endpointId}",
) : Device {


    override val diffId
        get() = ieeeAddress

    override fun hashCode(): Int = ieeeAddress.hashCode()

    fun command(input: ZigBeeInput<*>): ZigBeeCommand = input.from(this)
}

fun List<ZigBeeDevice>.createGroupSequence(groupName: String): List<ZigBeeCommand> {
    val result = mutableListOf<ZigBeeCommand>()

    val groupId = groupName.hashCode().toString()

    result.add(GroupAddCommand().command.let { ZigBeeCommand(it, listOf(it, groupId, groupName)) })
    result.addAll(map { device ->
        MembershipAddCommand().command.let { ZigBeeCommand(it, listOf(it, device.onOffAddress, groupId, groupName)) }
    })

    return result
}

internal fun ZigBeeNodeDao.device(): ZigBeeDevice? = ZigBeeDevice(
        name = ieeeAddress.toString(),
        node = this
)

private fun ZigBeeNodeDao.endpointFor(clusterType: ZclClusterType) = endpoints.firstOrNull { it.supports(clusterType) }

private fun ZigBeeEndpointDao.supports(clusterType: ZclClusterType) = inputClusters.map { it.clusterId }.contains(clusterType.id)

private fun nodeToZigBeeDevice(node: ZigBeeNodeDao) =
        node.endpoints.find {
            it
                    .inputClusters
                    .map { cluster -> cluster.clusterId }
                    .contains(ZclClusterType.ON_OFF.id)
        }
                ?.let { endpoint ->
//                    ZigBeeDevice(
//                            node.ieeeAddress.toString(),
//                            node.networkAddress.toString(),
//                            endpoint.endpointId.toString(),
//                            node.ieeeAddress.toString()
//                    )
                }