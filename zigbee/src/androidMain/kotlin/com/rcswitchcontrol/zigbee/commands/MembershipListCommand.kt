package com.rcswitchcontrol.zigbee.commands

import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.zigbee.R
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol
import com.rcswitchcontrol.zigbee.utilities.expect
import com.rcswitchcontrol.zigbee.utilities.getEndpoint
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.zsmartsystems.zigbee.zcl.ZclTransactionMatcher
import com.zsmartsystems.zigbee.zcl.clusters.groups.GetGroupMembershipCommand
import com.zsmartsystems.zigbee.zcl.clusters.groups.GetGroupMembershipResponse

/**
 * Lists group memberships from device.
 */
class MembershipListCommand : PayloadPublishingCommand by AbsZigBeeCommand(
        args = "[DEVICE]",
        commandString = "Joined Groups",
        descriptionString = "Lists group memberships from device.",
        processor = { _: CommsProtocol.Action, args: Array<out String> ->

            args.expect(2)

            val command = GetGroupMembershipCommand()

            command.groupCount = 0
            command.groupList = emptyList()
            command.destinationAddress = getEndpoint(args[1]).endpointAddress

            val result = sendTransaction(command, ZclTransactionMatcher()).get()

            when (result.isSuccess) {
                true -> ZigBeeProtocol.zigBeePayload(
                        response = "Member of groups:\n" +
                                result.getResponse<GetGroupMembershipResponse>().groupList.joinToString(separator = "\n")
                )
                else -> ZigBeeProtocol.zigBeePayload(response = "Error executing command: $result")
            }
        })