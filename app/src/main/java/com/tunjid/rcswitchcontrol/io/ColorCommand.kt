package com.tunjid.rcswitchcontrol.io

import com.tunjid.rcswitchcontrol.nsd.protocols.Cie
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

    override fun getCommand(): String = "color"

    override fun getDescription(): String = "Changes light color."

    override fun getSyntax(): String = "color DEVICEID RED GREEN BLUE"

    /**
     * {@inheritDoc}
     */
    @Throws(Exception::class)
    override fun process(networkManager: ZigBeeNetworkManager, args: Array<out String>, out: PrintStream) {
        invoke(args, 5,  networkManager, out) {
            val red: Float = parse(args[2])

            val green: Float = parse(args[3])

            val blue: Float = parse(args[4])

            color(it, networkManager, red.toDouble(), green.toDouble(), blue.toDouble(), 1.0)
        }
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
    fun color(destination: ZigBeeAddress,
              networkManager: ZigBeeNetworkManager,
              red: Double,
              green: Double,
              blue: Double, time: Double): Future<CommandResult>? {

        val cie = Cie.rgb2cie(red, green, blue)

        var x = (cie.x * 65536).toInt()
        var y = (cie.y * 65536).toInt()
        if (x > 65279) {
            x = 65279
        }
        if (y > 65279) {
            y = 65279
        }

        if (destination !is ZigBeeEndpointAddress) {
            return null
        }
        val endpoint = networkManager.getNode(destination.address)
                .getEndpoint(destination.endpoint) ?: return null
        val cluster = endpoint
                .getInputCluster(ZclColorControlCluster.CLUSTER_ID) as ZclColorControlCluster
        return cluster.moveToColorCommand(x, y, (time * 10).toInt())
    }
}