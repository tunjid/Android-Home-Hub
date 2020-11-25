/*
 * MIT License
 *
 * Copyright (c) 2019 Adetunji Dahunsi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.rcswitchcontrol.zigbee.commands

import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.asAction
import com.rcswitchcontrol.protocols.models.Payload
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol
import com.tunjid.rcswitchcontrol.common.serialize
import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import com.zsmartsystems.zigbee.console.ZigBeeConsoleAbstractCommand
import com.zsmartsystems.zigbee.console.ZigBeeConsoleCommand
import java.io.PrintStream

/**
 * Commands that push payloads out directly
 */
interface PayloadPublishingCommand : ZigBeeConsoleCommand

class AbsZigBeeCommand(
        private val args: String,
        private val descriptionString: String,
        private val commandString: String,
        private val log: Boolean = false,
        private val processor: ZigBeeNetworkManager.(action: CommsProtocol.Action, args: Array<out String>) -> Payload?
) : ZigBeeConsoleAbstractCommand(), PayloadPublishingCommand {

    override fun getCommand(): String = commandString

    override fun getDescription(): String = descriptionString

    override fun getSyntax(): String = "$command $args"

    override fun getHelp(): String = "Command: $command\nDescription: $description\n Syntax: $syntax"

    override fun process(networkManager: ZigBeeNetworkManager, args: Array<out String>, out: PrintStream) {
        if (log) out.println(ZigBeeProtocol
                .zigBeePayload(response = "Executing $commandString with args $args")
                .serialize())

        try {
            processor(networkManager, commandString.asAction, args)?.serialize()?.let(out::println)
            if (log) out.println(ZigBeeProtocol
                    .zigBeePayload(response = "Executed $commandString with args $args")
                    .serialize())
        } catch (e: Exception) {
            out.println(ZigBeeProtocol
                    .zigBeePayload(response = "Error executing $commandString. Message:\n${e.message}")
                    .serialize())
        }
    }
}