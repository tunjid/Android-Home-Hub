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
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol.Companion.zigBeePayload
import com.rcswitchcontrol.zigbee.utilities.findCluster
import com.rcswitchcontrol.zigbee.utilities.getEndpoint
import com.rcswitchcontrol.zigbee.utilities.pullAttributes
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.tunjid.rcswitchcontrol.common.serialize
import com.tunjid.rcswitchcontrol.common.serializeList
import com.zsmartsystems.zigbee.zcl.ZclCluster


/**
 * Switches a device on.
 */
class ReadDeviceAttributesCommand : PayloadPublishingCommand by AbsZigBeeCommand(
        args = "ENDPOINT CLUSTER ATTRIBUTE1 [ATTRIBUTE2 ...]",
        commandString = ContextProvider.appContext.getString(R.string.zigbeeprotocol_device_attributes),
        descriptionString = "Read one or more attributes from a device",
        processor = { action: CommsProtocol.Action, args: Array<out String> ->
            require(args.size >= 4) { "Invalid number of arguments" }

            val nodeAddress = args[1]
            val cluster: ZclCluster = getEndpoint(endpointId = nodeAddress)
                    .findCluster(clusterSpecifier = args[2])

            zigBeePayload(
                    action = action,
                    data = cluster.pullAttributes(nodeAddress = nodeAddress, attributeIds = args
                            .drop(3)
                            .filterNot { it.contains("=") }
                            .mapNotNull(String::toIntOrNull))
                            .serializeList()
            )
        }
)
