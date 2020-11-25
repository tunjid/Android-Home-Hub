package com.rcswitchcontrol.zigbee.commands

import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.zigbee.R
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol
import com.rcswitchcontrol.zigbee.utilities.expect
import com.rcswitchcontrol.zigbee.utilities.findDestination
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.zsmartsystems.zigbee.ZigBeeGroupAddress


/**
 * Removes group from network state but does not affect actual ZigBeet network.
 */
class GroupRemoveCommand : PayloadPublishingCommand by AbsZigBeeCommand(
        args = "GROUP",
        commandString = ContextProvider.appContext.getString(R.string.zigbeeprotocol_remove_group),
        descriptionString = "Removes group from gateway network state.",
        processor = { action: CommsProtocol.Action, args: Array<out String> ->
            args.expect(2)

            val address = findDestination(args[1]) as? ZigBeeGroupAddress
                    ?: throw IllegalArgumentException("The address provided is not a ZigBee Group")

            removeGroup(address.groupId)

            ZigBeeProtocol.zigBeePayload(
                    response = "Executed ${action.value} with args $args"
            )
        }
)