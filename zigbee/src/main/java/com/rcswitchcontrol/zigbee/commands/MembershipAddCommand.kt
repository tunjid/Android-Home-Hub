package com.rcswitchcontrol.zigbee.commands

import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.zigbee.R
import com.rcswitchcontrol.zigbee.utilities.expect
import com.rcswitchcontrol.zigbee.utilities.getEndpoint
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.zsmartsystems.zigbee.zcl.ZclTransactionMatcher
import com.zsmartsystems.zigbee.zcl.clusters.groups.AddGroupCommand

/**
 * Adds group membership to device.
 */
class MembershipAddCommand : PayloadPublishingCommand by AbsZigBeeCommand(
        log = true,
        args = "[DEVICE] [GROUPID] [GROUPNAME]",
        commandString = ContextProvider.appContext.getString(R.string.zigbeeprotocol_join_group),
        descriptionString = "Adds group membership to device.",
        processor = { _: CommsProtocol.Action, args: Array<out String> ->
            args.expect(4)

            val groupId: Int = args[2].toInt()
            val groupName = args[3]

            val command = AddGroupCommand()
            command.groupId = groupId
            command.groupName = groupName

            command.destinationAddress = getEndpoint(args[1]).endpointAddress

            sendTransaction(command, ZclTransactionMatcher())

            null
        }
)