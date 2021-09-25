package com.rcswitchcontrol.zigbee.commands

import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.zigbee.R
import com.tunjid.rcswitchcontrol.common.ContextProvider

/**
 * Views group name from device group membership.
 */
class MembershipViewCommand : PayloadPublishingCommand by AbsZigBeeCommand(
        args = "[DEVICE] [GROUPID]",
        commandString = "Name Group",
        descriptionString = "Views group name from device group membership.",
        processor = { _: CommsProtocol.Action, args: Array<out String> ->
//            args.expect(3)
//
//            val groupId = args[2].toInt()
//
//            val command = ViewGroupCommand()
//            command.groupId = groupId
//            command.destinationAddress = device.endpointAddress
//
//             sendTransaction(command, ZclTransactionMatcher())
//
//            if (!result.isSuccess) return out.println("Error executing command: $result")
//
//            val response = result.getResponse<ViewGroupResponse>()
//            val statusCode = response.status
//
//            if (statusCode == 0) out.println("Group name: " + response.groupName)
//            else out.println("Error reading group name: ${ZclStatus.getStatus(statusCode.toByte().toInt())}")

            null
        })