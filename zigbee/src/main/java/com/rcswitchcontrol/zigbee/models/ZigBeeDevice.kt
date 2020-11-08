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
import com.rcswitchcontrol.protocols.models.Device
import com.rcswitchcontrol.zigbee.commands.GroupAddCommand
import com.rcswitchcontrol.zigbee.commands.MembershipAddCommand
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol
import com.zsmartsystems.zigbee.database.ZclClusterDao
import com.zsmartsystems.zigbee.database.ZigBeeEndpointDao
import com.zsmartsystems.zigbee.database.ZigBeeNodeDao
import kotlinx.android.parcel.Parcelize

@Parcelize
data class ZigBeeDevice(
        override val name: String,
        internal val ieeeAddress: String,
        internal val networkAdress: String,
        internal val endpoints: List<Endpoint> = listOf(),
        override val key: String = ZigBeeProtocol::class.java.name
) : Parcelable, Device {

    internal val zigBeeId: String get() = "$networkAdress/$ieeeAddress"

    override val diffId
        get() = ieeeAddress

    fun command(input: ZigBeeInput<*>): ZigBeeCommand = input.from(this)

    override fun hashCode(): Int = ieeeAddress.hashCode()
}

fun List<ZigBeeDevice>.createGroupSequence(groupName: String): List<ZigBeeCommand> {
    val result = mutableListOf<ZigBeeCommand>()

    val groupId = groupName.hashCode().toString()

    result.add(GroupAddCommand().command.let { ZigBeeCommand(it, listOf(it, groupId, groupName)) })
    result.addAll(map { device ->
        MembershipAddCommand().command.let { ZigBeeCommand(it, listOf(it, device.zigBeeId, groupId, groupName)) }
    })

    return result
}

@Parcelize
data class Endpoint(
        val id: Int,
        val inputClusterIds: List<Int>
) : Parcelable

private val ZigBeeEndpointDao.endpoint
    get() = Endpoint(
            id = endpointId,
            inputClusterIds = inputClusters.map(ZclClusterDao::getClusterId) // may  match ZclClusterType.ON_OFF
    )

internal fun ZigBeeNodeDao.device(): ZigBeeDevice? =
        ZigBeeDevice(
                name = ieeeAddress.toString(),
                ieeeAddress = ieeeAddress.toString(),
                networkAdress = networkAddress.toString(),
                endpoints = endpoints.map(ZigBeeEndpointDao::endpoint)
        )
