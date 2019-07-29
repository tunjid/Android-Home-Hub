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

import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import com.zsmartsystems.zigbee.console.ZigBeeConsoleCommand
import java.io.PrintStream
import java.util.ArrayList

/**
 * Prints help on console.
 */
class HelpCommand(private val commands: Map<String, ZigBeeConsoleCommand>) : AbsZigBeeCommand() {
    override val args: String = "[COMMAND]"

    override fun getDescription(): String = "View command help."

    override fun getCommand(): String = "help"

    override fun process(networkManager: ZigBeeNetworkManager, args: Array<out String>, out: PrintStream) = when {
        args.size == 2 -> if (commands.containsKey(args[1])) {
            val command = commands[args[1]]
            out.push(command!!.description)
            out.push("")
            out.push("Syntax: " + command.syntax)
        } else throw IllegalArgumentException("Command ${args[1]} does not exist")

        args.size == 1 -> {
            val commandList = ArrayList(commands.keys)
            commandList.sort()
            out.push("Commands:")

            for (command in commands.keys) out.push("$command - ${commands.getValue(command).description}")
        }
        else -> throw IllegalArgumentException("Invalid command arguments")
    }
}