package com.tunjid.rcswitchcontrol.io

import com.zsmartsystems.zigbee.CommandResult
import com.zsmartsystems.zigbee.ZigBeeAddress
import com.zsmartsystems.zigbee.ZigBeeEndpointAddress
import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster
import java.io.PrintStream
import java.util.concurrent.Future

/**
 * Switches a device off.
 */
  class OffCommand : AbsZigBeeCommand() {

    override fun getCommand(): String = "off"

    override fun getDescription(): String = "Switches device off."

    override fun getSyntax(): String = "off DEVICEID/DEVICELABEL/GROUPID"

    @Throws(Exception::class)
    override fun process(networkManager: ZigBeeNetworkManager, args: Array<out String>, out: PrintStream) =
            invoke(args, 2,  networkManager, out) { off(it, networkManager) }

    /**
     * Switches destination off.
     *
     * @param destination the [ZigBeeAddress]
     * @return the command result future.
     */
    fun off(destination: ZigBeeAddress, networkManager: ZigBeeNetworkManager): Future<CommandResult>? {
        if (destination !is ZigBeeEndpointAddress) {
            return null
        }
        val endpoint = networkManager.getNode(destination.address)
                .getEndpoint(destination.endpoint) ?: return null
        val cluster = endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID) as ZclOnOffCluster
        return cluster.offCommand()
    }
}