package com.rcswitchcontrol.zigbee.commands

import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.zigbee.R
import com.rcswitchcontrol.zigbee.utilities.expect
import com.tunjid.rcswitchcontrol.common.ContextProvider

/**
 * Removes device group membership from device.
 */
class MembershipRemoveCommand : PayloadPublishingCommand by AbsZigBeeCommand(
        args = "[DEVICE] [GROUPID]",
        commandString = "Joined Groups",
        descriptionString = "Removes group membership from device.",
        processor = { action: CommsProtocol.Action, args: Array<out String> ->
            args.expect(3)

//            val groupId: Int = args[2].toInt()
//
//            val command = RemoveGroupCommand()
//            command.groupId = groupId
//            command.destinationAddress = device.endpointAddress
//
//             sendTransaction(command, ZclTransactionMatcher())

            null
        })