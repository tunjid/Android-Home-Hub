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

import com.rcswitchcontrol.zigbee.R
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.zsmartsystems.zigbee.CommandResult
import com.zsmartsystems.zigbee.ZigBeeAddress
import com.zsmartsystems.zigbee.ZigBeeEndpointAddress
import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster
import com.zsmartsystems.zigbee.zcl.clusters.onoff.OnCommand
import java.io.PrintStream
import java.util.concurrent.Future

/**
 * Switches a device on.
 */
class OnCommand : AbsZigBeeCommand() {
    override val args: String = "DEVICEID/DEVICELABEL/GROUPID"

    override fun getCommand(): String = ContextProvider.appContext.getString(R.string.zigbeeprotocol_on)

    override fun getDescription(): String = "Switches device on."

    @Throws(Exception::class)
    override fun process(networkManager: ZigBeeNetworkManager, args: Array<out String>, out: PrintStream) {
        args.expect(2)

        networkManager.findDestination(args[1]).then(
                { networkManager.on(it) },
                { onCommandProcessed(it, out) }
        )
    }

    private fun ZigBeeNetworkManager.on(destination: ZigBeeAddress): Future<CommandResult>? {
        if (destination !is ZigBeeEndpointAddress) return null

        val endpoint = getNode(destination.address).getEndpoint(destination.endpoint) ?: return null

        val cluster = endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID) as ZclOnOffCluster

        return cluster.sendCommand(OnCommand())
    }
}