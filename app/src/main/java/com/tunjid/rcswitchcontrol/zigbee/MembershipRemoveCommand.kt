package com.tunjid.rcswitchcontrol.zigbee

import com.rcswitchcontrol.protocols.ContextProvider
import com.tunjid.rcswitchcontrol.R
import com.zsmartsystems.zigbee.CommandResult
import com.zsmartsystems.zigbee.ZigBeeEndpoint
import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import com.zsmartsystems.zigbee.zcl.ZclTransactionMatcher
import com.zsmartsystems.zigbee.zcl.clusters.groups.RemoveGroupCommand
import java.io.PrintStream
import java.util.concurrent.Future

/**
 * Removes device group membership from device.
 */
class MembershipRemoveCommand : AbsZigBeeCommand() {
    override val args: String = "[DEVICE] [GROUPID]"

    override fun getCommand(): String = ContextProvider.appContext.getString(R.string.zigbeeprotocol_joined_groups)

    override fun getDescription(): String = "Removes group membership from device."

    @Throws(Exception::class)
    override fun process(networkManager: ZigBeeNetworkManager, args: Array<String>, out: PrintStream) {
        args.expect(3)

        val groupId: Int = args[2].toInt()

        networkManager.findDevice(args[1]).then(
                { networkManager.removeMembership(it, groupId) },
                { onCommandProcessed(it, out) }
        )
    }

    /**
     * Removes group membership from device.
     *
     * @param device the device
     * @param groupId the group ID
     * @return the command result future
     */
    private fun ZigBeeNetworkManager.removeMembership(device: ZigBeeEndpoint, groupId: Int): Future<CommandResult> {
        val command = RemoveGroupCommand()
        command.groupId = groupId
        command.destinationAddress = device.endpointAddress

        return sendTransaction(command, ZclTransactionMatcher())
    }
}