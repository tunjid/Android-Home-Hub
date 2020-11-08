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

import android.os.Parcelable
import android.util.Log
import com.rcswitchcontrol.protocols.models.Device
import com.rcswitchcontrol.zigbee.commands.GroupAddCommand
import com.rcswitchcontrol.zigbee.commands.MembershipAddCommand
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol
import com.tunjid.rcswitchcontrol.common.serialize
import com.zsmartsystems.zigbee.database.ZigBeeEndpointDao
import com.zsmartsystems.zigbee.database.ZigBeeNodeDao
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType
import kotlinx.android.parcel.Parcelize

@Parcelize
data class ZigBeeDevice(
        override val name: String,
        internal val ieeeAddress: String,
        internal val networkAdress: String,
        internal val endpoints: List<Endpoint> = listOf(),
        override val key: String = ZigBeeProtocol::class.java.name
) : Parcelable, Device {

    internal val onOffAddress: String get() = "$networkAdress/${endpointFor(ZclClusterType.ON_OFF)?.id}"
    internal val levelAddress: String get() = "$networkAdress/${endpointFor(ZclClusterType.LEVEL_CONTROL)?.id}"
    internal val colorAddress: String get() = "$networkAdress/${endpointFor(ZclClusterType.COLOR_CONTROL)?.id}"

    override val diffId
        get() = ieeeAddress

    override fun hashCode(): Int = ieeeAddress.hashCode()

    fun command(input: ZigBeeInput<*>): ZigBeeCommand = input.from(this)

    private fun endpointFor(clusterType: ZclClusterType) = endpoints.firstOrNull { it.supports(clusterType) }

    private fun Endpoint.supports(clusterType: ZclClusterType) = inputClusters.map(Cluster::id).contains(clusterType.id)
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

@Parcelize
data class Endpoint(
        val id: Int,
        val inputClusters: List<Cluster>
) : Parcelable

@Parcelize
data class Cluster(
        val id: Int
) : Parcelable

private val ZigBeeEndpointDao.endpoint: Endpoint
    get() {
        Log.i("TEST", "Endpoint is ${this.serialize()}")
        return Endpoint(
                id = endpointId,
                inputClusters = inputClusters.map { Cluster(it.clusterId) } // may  match ZclClusterType.ON_OFF
        )
    }

internal fun ZigBeeNodeDao.device(): ZigBeeDevice? {
    Log.i("TEST", "Node is ${this.serialize()}")

    return ZigBeeDevice(
            name = ieeeAddress.toString(),
            ieeeAddress = ieeeAddress.toString(),
            networkAdress = networkAddress.toString(),
            endpoints = endpoints.map(ZigBeeEndpointDao::endpoint)
    )
}

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