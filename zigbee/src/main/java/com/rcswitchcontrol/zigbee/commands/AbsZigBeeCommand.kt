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

import com.zsmartsystems.zigbee.CommandResult
import com.zsmartsystems.zigbee.ZigBeeAddress
import com.zsmartsystems.zigbee.ZigBeeEndpoint
import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import com.zsmartsystems.zigbee.console.ZigBeeConsoleCommand
import java.io.PrintStream
import java.util.concurrent.Future

abstract class AbsZigBeeCommand : ZigBeeConsoleCommand {

    override fun getSyntax(): String = "$command $args"

    override fun getHelp(): String = "Command: $command\nDescription: $description\n Syntax: $syntax"

    abstract val args: String

    protected fun Array<out String>.expect(expected: Int) {
        if (size != expected) throw IllegalArgumentException("Invalid number of command arguments, expected $expected, got $size")
    }

    protected fun <T> T?.then(function: (T) -> Future<CommandResult>?, done: (CommandResult) -> Unit) {
        this ?: throw IllegalArgumentException("Target not found")
        val result = function.invoke(this)?.get()
                ?: throw IllegalArgumentException("Unable to execute command")

        done(result)
    }

    /**
     * Default processing for command result.
     *
     * @param result the command result
     * @param out the output
     * @return TRUE if result is success
     */
    protected open fun onCommandProcessed(result: CommandResult, out: PrintStream) =
            if (result.isSuccess) out.println("Success response received.")
            else out.println("Error executing command \"$command\": $result")

    /**
     * Gets device by device identifier.
     *
     * @param deviceIdentifier the device identifier
     * @return the device
     */

    protected fun ZigBeeNetworkManager.findDevice(deviceIdentifier: String): ZigBeeEndpoint? {
        for (node in nodes)
            for (endpoint in node.endpoints)
                if (deviceIdentifier == node.networkAddress.toString() + "/" + endpoint.endpointId)
                    return endpoint

        return null
    }

    /**
     * Gets destination by device identifier or group ID.
     *
     * @param destinationIdentifier the device identifier or group ID
     * @return the device
     */
    protected fun ZigBeeNetworkManager.findDestination(destinationIdentifier: String): ZigBeeAddress? {
        findDevice(destinationIdentifier)?.let { return it.endpointAddress }
        try {
            for (group in groups) if (destinationIdentifier == group.label) return group

            val groupId = destinationIdentifier.toInt()
            return getGroup(groupId)
        } catch (e: NumberFormatException) {
            return null
        }
    }

    fun PrintStream.push(line: String) =  println(line)

}