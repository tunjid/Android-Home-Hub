package com.tunjid.rcswitchcontrol.zigbee

import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.R
import com.zsmartsystems.zigbee.CommandResult
import com.zsmartsystems.zigbee.ZigBeeEndpoint
import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import com.zsmartsystems.zigbee.zcl.ZclTransactionMatcher
import com.zsmartsystems.zigbee.zcl.clusters.groups.AddGroupCommand
import java.io.PrintStream
import java.util.concurrent.Future

/**
 * Adds group membership to device.
 */
class MembershipAddCommand : AbsZigBeeCommand() {
    override val args: String = "[DEVICE] [GROUPID] [GROUPNAME]"

    override fun getCommand(): String = App.instance.getString(R.string.zigbeeprotocol_join_group)

    override fun getDescription(): String = "Adds group membership to device."

    @Throws(Exception::class)
    override fun process(networkManager: ZigBeeNetworkManager, args: Array<String>, out: PrintStream) {
        args.expect(4)

        val groupId: Int = args[2].toInt()
        val groupName = args[3]

        networkManager.findDevice(args[1]).then(
                { networkManager.addMembership(it, groupId, groupName) },
                { onCommandProcessed(it, out) }
        )
    }

    /**
     * Adds group membership to device.
     *
     * @param device the device
     * @param groupId the group ID
     * @param groupName the group name
     * @return the command result future
     */
    private fun ZigBeeNetworkManager.addMembership(device: ZigBeeEndpoint, groupId: Int, groupName: String): Future<CommandResult> {
        val command = AddGroupCommand()
        command.groupId = groupId
        command.groupName = groupName

        command.destinationAddress = device.endpointAddress

        return sendTransaction(command, ZclTransactionMatcher())
    }
}