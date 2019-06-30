package com.tunjid.rcswitchcontrol.zigbee

import com.zsmartsystems.zigbee.IeeeAddress
import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import java.io.PrintStream

/**
 * Rediscover a node from its IEEE address.
 */
class RediscoverCommand : AbsZigBeeCommand() {

    override fun getCommand(): String = "rediscover"

    override fun getDescription(): String = "Rediscover a node from its IEEE address."

    override fun getSyntax(): String = "rediscover IEEEADDRESS"

    @Throws(Exception::class)
    override fun process(networkManager: ZigBeeNetworkManager, args: Array<out String>, out: PrintStream) {
        if (args.size != 2) throw IllegalArgumentException("Invalid command arguments")

        val address = IeeeAddress(args[1])

        print("Sending rediscovery request for address $address", out)
        networkManager.rediscoverNode(address)
    }
}