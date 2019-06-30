package com.tunjid.rcswitchcontrol.zigbee

import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import com.zsmartsystems.zigbee.console.ZigBeeConsoleCommand
import java.io.PrintStream
import java.util.ArrayList

/**
 * Prints help on console.
 */
class HelpCommand(private val commands: Map<String, ZigBeeConsoleCommand>) : AbsZigBeeCommand() {

    override fun getDescription(): String = "View command help."

    override fun getCommand(): String = "help"

    override fun getSyntax(): String = "help [command]"

    override fun process(networkManager: ZigBeeNetworkManager, args: Array<out String>, out: PrintStream) = when {
        args.size == 2 -> if (commands.containsKey(args[1])) {
            val command = commands[args[1]]
            print(command!!.description, out)
            print("", out)
            print("Syntax: " + command.syntax, out)
        } else {
            throw IllegalArgumentException("Command ${args[1]} does not exist")
        }
        args.size == 1 -> {
            val commandList = ArrayList(commands.keys)
            commandList.sort()
            print("Commands:", out)

            for (command in commands.keys) {
                print(command + " - " + commands.getValue(command).description, out)
            }
        }
        else -> throw IllegalArgumentException("Invalid command arguments")
    }
}