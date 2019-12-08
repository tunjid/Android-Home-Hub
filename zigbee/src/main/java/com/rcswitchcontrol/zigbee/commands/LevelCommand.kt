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

import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.rcswitchcontrol.zigbee.R
import com.zsmartsystems.zigbee.CommandResult
import com.zsmartsystems.zigbee.ZigBeeAddress
import com.zsmartsystems.zigbee.ZigBeeEndpointAddress
import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster
import java.io.PrintStream
import java.util.concurrent.Future

/**
 * Changes a device level for example lamp brightness.
 */
class LevelCommand : AbsZigBeeCommand() {
    override val args: String = "DEVICEID LEVEL [RATE]"

    override fun getCommand(): String = ContextProvider.appContext.getString(R.string.zigbeeprotocol_level)

    override fun getDescription(): String = "Changes device level for example lamp brightness, where LEVEL is between 0 and 1."

    @Throws(Exception::class)
    override fun process(networkManager: ZigBeeNetworkManager, args: Array<out String>, out: PrintStream) {
        args.expect(3)

        val level = args[2].toDouble()
        val time = if (args.size == 4) args[3].toDouble() else 1.0

        networkManager.findDestination(args[1]).then(
                { networkManager.level(it, level, time) },
                { onCommandProcessed(it, out) }
        )
    }

    /**
     * Moves device level.
     *
     * @param destination the [ZigBeeAddress]
     * @param level the level
     * @param time the transition time
     * @return the command result future.
     */
    private fun ZigBeeNetworkManager.level(destination: ZigBeeAddress, level: Double, time: Double): Future<CommandResult>? {
        var l = (level * 254).toInt()

        if (l > 254) l = 254
        if (l < 0) l = 0

        if (destination !is ZigBeeEndpointAddress) return null

        val endpoint = getNode(destination.address).getEndpoint(destination.endpoint) ?: return null

        val cluster = endpoint.getInputCluster(ZclLevelControlCluster.CLUSTER_ID) as ZclLevelControlCluster

        return cluster.moveToLevelWithOnOffCommand(l, (time * 10).toInt())
    }
}