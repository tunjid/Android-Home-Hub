package com.rcswitchcontrol.zigbee.commands

import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.zigbee.R
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol
import com.tunjid.rcswitchcontrol.common.ContextProvider

/**
 * Lists groups in gateway network state.
 */
class GroupListCommand : PayloadPublishingCommand by AbsZigBeeCommand(
        args = "",
        commandString = ContextProvider.appContext.getString(R.string.zigbeeprotocol_list_groups),
        descriptionString = "Lists groups in gateway network state.",
        processor = { _: CommsProtocol.Action, _: Array<out String> ->
            ZigBeeProtocol.zigBeePayload(
                    response = groups.joinToString(separator = "\n") { group ->
                        "${group.groupId.toString().padStart(10)}      ${group.label}"
                    }
            )
        })