package com.tunjid.rcswitchcontrol.zigbee

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

    override fun getCommand(): String = "level"

    override fun getDescription(): String = "Changes device level for example lamp brightness, where LEVEL is between 0 and 1."

    override fun getSyntax(): String = "level DEVICEID LEVEL [RATE]"

    @Throws(Exception::class)
    override fun process(networkManager: ZigBeeNetworkManager, args: Array<out String>, out: PrintStream) {
        invoke(args, 3, networkManager, out) {
            val level: Float = parse(args[2])
            val time = if (args.size == 4) parse(args[3]) else 1.0.toFloat()

            level(it, networkManager, level.toDouble(), time.toDouble())
        }
    }

    /**
     * Moves device level.
     *
     * @param destination the [ZigBeeAddress]
     * @param level the level
     * @param time the transition time
     * @return the command result future.
     */
    fun level(destination: ZigBeeAddress, networkManager: ZigBeeNetworkManager, level: Double, time: Double): Future<CommandResult>? {

        var l = (level * 254).toInt()
        if (l > 254) {
            l = 254
        }
        if (l < 0) {
            l = 0
        }

        if (destination !is ZigBeeEndpointAddress) {
            return null
        }
        val endpoint = networkManager.getNode(destination.address)
                .getEndpoint(destination.endpoint) ?: return null
        val cluster = endpoint
                .getInputCluster(ZclLevelControlCluster.CLUSTER_ID) as ZclLevelControlCluster
        return cluster.moveToLevelWithOnOffCommand(l, (time * 10).toInt())
    }
}