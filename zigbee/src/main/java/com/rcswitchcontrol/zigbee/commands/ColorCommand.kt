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
import com.rcswitchcontrol.zigbee.utilities.Cie
import com.zsmartsystems.zigbee.CommandResult
import com.zsmartsystems.zigbee.ZigBeeAddress
import com.zsmartsystems.zigbee.ZigBeeEndpointAddress
import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster
import java.io.PrintStream
import java.util.concurrent.Future

/**
 * Changes a light color on device.
 */
class ColorCommand : AbsZigBeeCommand() {
    override val args: String = "DEVICEID RED GREEN BLUE"

    override fun getCommand(): String = ContextProvider.appContext.getString(R.string.zigbeeprotocol_color)

    override fun getDescription(): String = "Changes light color."

    @Throws(Exception::class)
    override fun process(networkManager: ZigBeeNetworkManager, args: Array<out String>, out: PrintStream) {
        args.expect(5)

        val red = args[2].toDouble()
        val green = args[3].toDouble()
        val blue = args[4].toDouble()

        networkManager.findDestination(args[1]).then(
                { networkManager.color(it, red, green, blue, 1.0) },
                { onCommandProcessed(it, out) }
        )
    }

    /**
     * Colors device light.
     *
     * @param destination the [ZigBeeAddress]
     * @param red the red component [0..1]
     * @param green the green component [0..1]
     * @param blue the blue component [0..1]
     * @param time the in seconds
     * @return the command result future.
     */
    private fun ZigBeeNetworkManager.color(destination: ZigBeeAddress,
                                           red: Double,
                                           green: Double,
                                           blue: Double, time: Double): Future<CommandResult>? {
        val cie = Cie.rgb2cie(red, green, blue)

        var x = (cie.x * 65536).toInt()
        var y = (cie.y * 65536).toInt()

        if (x > 65279) x = 65279
        if (y > 65279) y = 65279

        if (destination !is ZigBeeEndpointAddress) return null

        val endpoint = getNode(destination.address).getEndpoint(destination.endpoint) ?: return null
        val cluster = endpoint.getInputCluster(ZclColorControlCluster.CLUSTER_ID) as ZclColorControlCluster

        return cluster.moveToColorCommand(x, y, (time * 10).toInt())
    }
}