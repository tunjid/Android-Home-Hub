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

package com.rcswitchcontrol.zigbee.commands

import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.zigbee.R
import com.rcswitchcontrol.zigbee.models.Value
import com.rcswitchcontrol.zigbee.models.ZigBeeAttribute
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol
import com.rcswitchcontrol.zigbee.utilities.addressOf
import com.rcswitchcontrol.zigbee.utilities.expect
import com.rcswitchcontrol.zigbee.utilities.trifecta
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.tunjid.rcswitchcontrol.common.serializeList
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster

/**
 * Switches a device off.
 */
class OffCommand : PayloadPublishingCommand by AbsZigBeeCommand(
    args = "DEVICEID/DEVICELABEL/GROUPID",
    commandString = ContextProvider.appContext.getString(R.string.zigbeeprotocol_off),
    descriptionString = "Switches device off.",
    processor = { action: CommsProtocol.Action, args: Array<out String> ->
        args.expect(2)

        val (node, endpoint, cluster) = trifecta<ZclOnOffCluster>(
            lookUpId = args[1],
            clusterId = ZclOnOffCluster.CLUSTER_ID
        )
        val result = cluster.offCommand().get()

        when (result.isSuccess) {
            true -> ZigBeeProtocol.zigBeePayload(
                action = action,
                data = listOf(ZigBeeAttribute(
                    nodeAddress = node.addressOf(endpoint),
                    attributeId = ZclOnOffCluster.ATTR_ONOFF,
                    endpointId = endpoint.endpointId,
                    clusterId = cluster.clusterId,
                    type = Boolean::class.java.simpleName,
                    value = Value.Boolean(false)
                ))
                    .serializeList()
            )
            else -> ZigBeeProtocol.zigBeePayload(
                response = "Error turning off device:\n¬$result"
            )
        }
    }
)
