package com.tunjid.rcswitchcontrol.zigbee

import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.R
import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import java.io.PrintStream

/**
 * Lists groups in gateway network state.
 */
class GroupListCommand : AbsZigBeeCommand() {
    override fun getCommand(): String = App.instance.getString(R.string.zigbeeprotocol_list_groups)

    override fun getDescription(): String = "Lists groups in gateway network state."

    override fun getSyntax(): String = "grouplist"

    override fun process(networkManager: ZigBeeNetworkManager, args: Array<String>, out: PrintStream) {
        val groups = networkManager.groups

        for (group in groups) {
            print("${group.groupId.toString().padStart(10)}      ${group.label}", out)
        }
    }
}