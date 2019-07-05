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

package com.tunjid.rcswitchcontrol.zigbee

import com.zsmartsystems.zigbee.CommandResult
import com.zsmartsystems.zigbee.ZigBeeAddress
import com.zsmartsystems.zigbee.ZigBeeEndpoint
import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import com.zsmartsystems.zigbee.console.ZigBeeConsoleCommand
import java.io.PrintStream
import java.util.concurrent.Future

abstract class AbsZigBeeCommand : ZigBeeConsoleCommand {

    override fun getHelp(): String = "Command: $command\nDescription: $description\n Syntax: $syntax"

    fun invoke(
            args: Array<out String>,
            expectedArgs: Int,
            networkManager: ZigBeeNetworkManager,
            out: PrintStream,
            implementation: (destination: ZigBeeAddress) -> Future<CommandResult>?) {
        if (args.size != expectedArgs) throw IllegalArgumentException("Invalid command arguments")

        val destination = getDestination(args[1], networkManager)
                ?: throw IllegalArgumentException("Destination not found")

        val response = implementation(destination)?.get()
                ?: throw IllegalArgumentException("Unable to execute command")

        defaultResponseProcessing(response, out)
    }

    /**
     * Default processing for command result.
     *
     * @param result the command result
     * @param out the output
     * @return TRUE if result is success
     */
    private fun defaultResponseProcessing(result: CommandResult, out: PrintStream) =
            if (result.isSuccess) out.println("Success response received.")
            else out.println("Error executing command \"$command\": $result")

    /**
     * Gets destination by device identifier or group ID.
     *
     * @param destinationIdentifier the device identifier or group ID
     * @return the device
     */
    private fun getDestination(
            destinationIdentifier: String,
            networkManager: ZigBeeNetworkManager
    ): ZigBeeAddress? {
        val device = getDevice(destinationIdentifier, networkManager)

        if (device != null) return device.endpointAddress

        try {
            for (group in networkManager.groups) if (destinationIdentifier == group.label) return group

            val groupId = Integer.parseInt(destinationIdentifier)
            return networkManager.getGroup(groupId)
        } catch (e: NumberFormatException) {
            return null
        }
    }

    /**
     * Gets device by device identifier.
     *
     * @param deviceIdentifier the device identifier
     * @return the device
     */
    private fun getDevice(
            deviceIdentifier: String,
            networkManager: ZigBeeNetworkManager
    ): ZigBeeEndpoint? {
        for (node in networkManager.nodes)
            for (endpoint in node.endpoints)
                if (deviceIdentifier == node.networkAddress.toString() + "/" + endpoint.endpointId)
                    return endpoint

        return null
    }

    /**
     * Prints line to console.
     *
     * @param line the line
     */
    fun print(line: String, out: PrintStream) = out.println("\r" + line)

    fun parse(value: String): Float = try {
        java.lang.Float.parseFloat(value)
    } catch (e: NumberFormatException) {
        throw IllegalArgumentException("Invalid blue color")
    }
}