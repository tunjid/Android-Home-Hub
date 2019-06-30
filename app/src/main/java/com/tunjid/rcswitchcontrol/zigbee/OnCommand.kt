package com.tunjid.rcswitchcontrol.zigbee

import com.zsmartsystems.zigbee.CommandResult
import com.zsmartsystems.zigbee.ZigBeeAddress
import com.zsmartsystems.zigbee.ZigBeeEndpointAddress
import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster
import java.io.PrintStream
import java.util.concurrent.Future

/**
 * Switches a device on.
 */
class OnCommand : AbsZigBeeCommand() {

    override fun getCommand(): String = "on"

    override fun getDescription(): String = "Switches device on."

    override fun getSyntax(): String = "on DEVICEID/DEVICELABEL/GROUPID"

    @Throws(Exception::class)
    override fun process(networkManager: ZigBeeNetworkManager, args: Array<out String>, out: PrintStream) =
            invoke(args, 2,  networkManager, out) { on(it, networkManager) }

    fun on(destination: ZigBeeAddress, networkManager: ZigBeeNetworkManager): Future<CommandResult>? {
        if (destination !is ZigBeeEndpointAddress) {
            return null
        }
        val endpoint = networkManager.getNode(destination.address)
                .getEndpoint(destination.endpoint) ?: return null
        val cluster = endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID) as ZclOnOffCluster
        return cluster.onCommand()
    }
}