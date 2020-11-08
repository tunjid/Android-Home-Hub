package com.rcswitchcontrol.zigbee.commands

import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.rcswitchcontrol.zigbee.R
import com.zsmartsystems.zigbee.CommandResult
import com.zsmartsystems.zigbee.ZigBeeEndpoint
import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import com.zsmartsystems.zigbee.zcl.ZclStatus
import com.zsmartsystems.zigbee.zcl.ZclTransactionMatcher
import com.zsmartsystems.zigbee.zcl.clusters.groups.ViewGroupCommand
import com.zsmartsystems.zigbee.zcl.clusters.groups.ViewGroupResponse
import java.io.PrintStream
import java.util.concurrent.Future

/**
 * Views group name from device group membership.
 */
class MembershipViewCommand : AbsZigBeeCommand() {
    override val args: String = "[DEVICE] [GROUPID]"

    override fun getCommand(): String = ContextProvider.appContext.getString(R.string.zigbeeprotocol_name_group)

    override fun getDescription(): String = "Views group name from device group membership."

    @Throws(Exception::class)
    override fun process(networkManager: ZigBeeNetworkManager, args: Array<String>, out: PrintStream) {
        args.expect(3)

        val groupId = args[2].toInt()

        networkManager.findDevice(args[1]).then(
                { networkManager.viewMembership(it, groupId) },
                { onCommandProcessed(it, out) }
        )
    }

    override fun onCommandProcessed(result: CommandResult, out: PrintStream) {
        if (!result.isSuccess) return out.println("Error executing command: $result")

        val response = result.getResponse<ViewGroupResponse>()
        val statusCode = response.status

        if (statusCode == 0) out.println("Group name: " + response.groupName)
        else out.println("Error reading group name: ${ZclStatus.getStatus(statusCode.toByte().toInt())}")
    }

    /**
     * Views group membership from device.
     *
     * @param device the device
     * @param groupId the group ID
     * @return the command result future
     */
    fun ZigBeeNetworkManager.viewMembership(device: ZigBeeEndpoint, groupId: Int): Future<CommandResult> {
        val command = ViewGroupCommand(groupId)
        command.destinationAddress = device.endpointAddress

        return sendTransaction(command, ZclTransactionMatcher())
    }
}