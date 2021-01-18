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
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster

/**
 * Changes a device level for example lamp brightness.
 */
class LevelCommand : PayloadPublishingCommand by AbsZigBeeCommand(
    args = "DEVICEID LEVEL [RATE]",
    commandString = ContextProvider.appContext.getString(R.string.zigbeeprotocol_level),
    descriptionString = "Changes device level for example lamp brightness, where LEVEL is between 0 and 1.",
    processor = { action: CommsProtocol.Action, args: Array<out String> ->
        args.expect(3)

        val level = args[2].toDouble()
        val time = if (args.size == 4) args[3].toDouble() else 1.0

        var l = (level * 254).toInt()

        if (l > 254) l = 254
        if (l < 0) l = 0

        val (node, endpoint, cluster) = trifecta<ZclLevelControlCluster>(
            lookUpId = args[1],
            clusterId = ZclLevelControlCluster.CLUSTER_ID
        )
        val result = cluster.moveToLevelWithOnOffCommand(l, (time * 10).toInt()).get()

        when (result.isSuccess) {
            true -> ZigBeeProtocol.zigBeePayload(
                action = action,
                data = listOf(ZigBeeAttribute(
                    nodeAddress = node.addressOf(endpoint),
                    attributeId = ZclLevelControlCluster.ATTR_CURRENTLEVEL,
                    endpointId = endpoint.endpointId,
                    clusterId = cluster.clusterId,
                    type = Int::class.java.simpleName,
                    value = Value.Int(l)
                ))
                    .serializeList()
            )
            else -> ZigBeeProtocol.zigBeePayload(
                response = "Error changing device level:\n$result"
            )
        }
    }
)
