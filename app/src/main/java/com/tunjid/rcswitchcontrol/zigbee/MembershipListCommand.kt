package com.tunjid.rcswitchcontrol.zigbee

import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.R
import com.zsmartsystems.zigbee.CommandResult
import com.zsmartsystems.zigbee.ZigBeeEndpoint
import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import com.zsmartsystems.zigbee.zcl.ZclTransactionMatcher
import com.zsmartsystems.zigbee.zcl.clusters.groups.GetGroupMembershipCommand
import com.zsmartsystems.zigbee.zcl.clusters.groups.GetGroupMembershipResponse
import java.io.PrintStream
import java.util.*
import java.util.concurrent.Future

/**
 * Lists group memberships from device.
 */
class MembershipListCommand : AbsZigBeeCommand() {
    override val args: String = "[DEVICE]"

    override fun getCommand(): String = App.instance.getString(R.string.zigbeeprotocol_joined_groups)

    override fun getDescription(): String = "Lists group memberships from device."

    @Throws(Exception::class)
    override fun process(networkManager: ZigBeeNetworkManager, args: Array<String>, out: PrintStream) {
        args.expect(2)

        networkManager.findDevice(args[1]).then(
                { networkManager.getGroupMemberships(it) },
                { onCommandProcessed(it, out) }
        )
    }

    override fun onCommandProcessed(result: CommandResult, out: PrintStream) {
        if (!result.isSuccess) return out.println("Error executing command: $result")

        val response = result.getResponse<GetGroupMembershipResponse>()
        out.print("Member of groups:")

        for (value in response.groupList) {
            out.print(' ')
            out.print(value)
        }
        out.println()
    }

    /**
     * Gets group memberships from device.
     *
     * @param device the device
     * @return the command result future
     */
    private fun ZigBeeNetworkManager.getGroupMemberships(device: ZigBeeEndpoint): Future<CommandResult> {
        val command = GetGroupMembershipCommand()

        command.groupCount = 0
        command.groupList = Collections.emptyList()
        command.destinationAddress = device.endpointAddress

        return sendTransaction(command, ZclTransactionMatcher())
    }
}