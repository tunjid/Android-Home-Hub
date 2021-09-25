package com.rcswitchcontrol.zigbee.commands

import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.zigbee.R
import com.rcswitchcontrol.zigbee.utilities.expect
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.zsmartsystems.zigbee.ZigBeeGroupAddress

/**
 * Adds group to gateway network state. Does not affect actual ZigBee network.
 */
class GroupAddCommand : PayloadPublishingCommand by AbsZigBeeCommand(
        log = true,
        args = "GROUPID GROUPLABEL",
        commandString = "Add Group",
        descriptionString = "Adds group to gateway network state.",
        processor = { _: CommsProtocol.Action, args: Array<out String> ->
            args.expect(3)

            addGroup(ZigBeeGroupAddress().apply {
                groupId = args[1].toInt()
                label = args[2]
            })
            null
        })