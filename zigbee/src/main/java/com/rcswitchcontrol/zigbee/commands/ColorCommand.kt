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
import com.rcswitchcontrol.zigbee.models.ZigBeeAttribute
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol
import com.rcswitchcontrol.zigbee.utilities.Cie
import com.rcswitchcontrol.zigbee.utilities.addressOf
import com.rcswitchcontrol.zigbee.utilities.expect
import com.rcswitchcontrol.zigbee.utilities.trifecta
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.tunjid.rcswitchcontrol.common.serialize
import com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster

/**
 * Changes a light color on device.
 */
class ColorCommand : PayloadPublishingCommand by AbsZigBeeCommand(
        args = "DEVICEID RED GREEN BLUE",
        commandString = ContextProvider.appContext.getString(R.string.zigbeeprotocol_color),
        descriptionString = "Changes light color.",
        processor = { action: CommsProtocol.Action, args: Array<out String> ->
            args.expect(5)

            val red = args[2].toDouble()
            val green = args[3].toDouble()
            val blue = args[4].toDouble()
            val time = 1.0

            val cie = Cie.rgb2cie(red, green, blue)

            var x = (cie.x * 65536).toInt()
            var y = (cie.y * 65536).toInt()

            if (x > 65279) x = 65279
            if (y > 65279) y = 65279

            val (node, endpoint, cluster) = trifecta<ZclColorControlCluster>(
                    lookUpId = args[1],
                    clusterId = ZclColorControlCluster.CLUSTER_ID
            )
            val result = cluster.moveToColorCommand(x, y, (time * 10).toInt()).get()

            when (result.isSuccess) {
                true -> ZigBeeProtocol.zigBeePayload(
                        action = action,
                        data = listOf(
                                ZigBeeAttribute(
                                        nodeAddress = node.addressOf(endpoint),
                                        attributeId = ZclColorControlCluster.ATTR_CURRENTX,
                                        endpointId = endpoint.endpointId,
                                        clusterId = cluster.clusterId,
                                        type = Int::class.java.simpleName,
                                        value = x
                                ),
                                ZigBeeAttribute(
                                        nodeAddress = node.addressOf(endpoint),
                                        attributeId = ZclColorControlCluster.ATTR_CURRENTY,
                                        endpointId = endpoint.endpointId,
                                        clusterId = cluster.clusterId,
                                        type = Int::class.java.simpleName,
                                        value = y
                                )
                        )
                                .serialize()
                )
                else -> ZigBeeProtocol.zigBeePayload(
                        response = "Error changing color of device $result"
                )
            }
        }
)
