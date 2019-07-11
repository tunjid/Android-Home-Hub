package com.tunjid.rcswitchcontrol.zigbee

import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.R
import com.zsmartsystems.zigbee.ZigBeeGroupAddress
import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import java.io.PrintStream

/**
 * Adds group to gateway network state. Does not affect actual ZigBee network.
 */
class GroupAddCommand : AbsZigBeeCommand() {
    override val args: String = "GROUPID GROUPLABEL"

    override fun getCommand(): String = App.instance.getString(R.string.zigbeeprotocol_add_group)

    override fun getDescription(): String = "Adds group to gateway network state."

    @Throws(Exception::class)
    override fun process(networkManager: ZigBeeNetworkManager, args: Array<String>, out: PrintStream) {
        args.expect(3)

        networkManager.addGroup(ZigBeeGroupAddress().apply {
            groupId = args[1].toInt()
            label = args[2]
        })
    }
}