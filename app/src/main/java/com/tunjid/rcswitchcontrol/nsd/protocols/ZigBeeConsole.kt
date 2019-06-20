package com.tunjid.rcswitchcontrol.nsd.protocols

import android.os.Build
import com.zsmartsystems.zigbee.CommandResult
import com.zsmartsystems.zigbee.IeeeAddress
import com.zsmartsystems.zigbee.ZigBeeAddress
import com.zsmartsystems.zigbee.ZigBeeEndpoint
import com.zsmartsystems.zigbee.ZigBeeEndpointAddress
import com.zsmartsystems.zigbee.ZigBeeGroupAddress
import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import com.zsmartsystems.zigbee.ZigBeeNetworkNodeListener
import com.zsmartsystems.zigbee.ZigBeeNode
import com.zsmartsystems.zigbee.app.basic.ZigBeeBasicServerExtension
import com.zsmartsystems.zigbee.app.discovery.ZigBeeDiscoveryExtension
import com.zsmartsystems.zigbee.app.iasclient.ZigBeeIasCieExtension
import com.zsmartsystems.zigbee.app.otaserver.ZclOtaUpgradeServer
import com.zsmartsystems.zigbee.app.otaserver.ZigBeeOtaFile
import com.zsmartsystems.zigbee.app.otaserver.ZigBeeOtaUpgradeExtension
import com.zsmartsystems.zigbee.console.*
import com.zsmartsystems.zigbee.security.ZigBeeKey
import com.zsmartsystems.zigbee.transport.TransportConfig
import com.zsmartsystems.zigbee.transport.TransportConfigOption
import com.zsmartsystems.zigbee.transport.TrustCentreJoinMode
import com.zsmartsystems.zigbee.transport.ZigBeeTransportFirmwareUpdate
import com.zsmartsystems.zigbee.transport.ZigBeeTransportTransmit
import com.zsmartsystems.zigbee.zcl.ZclStatus
import com.zsmartsystems.zigbee.zcl.ZclTransactionMatcher
import com.zsmartsystems.zigbee.zcl.clusters.ZclColorControlCluster
import com.zsmartsystems.zigbee.zcl.clusters.ZclLevelControlCluster
import com.zsmartsystems.zigbee.zcl.clusters.ZclOnOffCluster
import com.zsmartsystems.zigbee.zcl.clusters.ZclOtaUpgradeCluster
import com.zsmartsystems.zigbee.zcl.clusters.general.ConfigureReportingResponse
import com.zsmartsystems.zigbee.zcl.clusters.general.ReportAttributesCommand
import com.zsmartsystems.zigbee.zcl.clusters.groups.AddGroupCommand
import com.zsmartsystems.zigbee.zcl.clusters.groups.GetGroupMembershipCommand
import com.zsmartsystems.zigbee.zcl.clusters.groups.GetGroupMembershipResponse
import com.zsmartsystems.zigbee.zcl.clusters.groups.RemoveGroupCommand
import com.zsmartsystems.zigbee.zcl.clusters.groups.ViewGroupCommand
import com.zsmartsystems.zigbee.zcl.clusters.groups.ViewGroupResponse
import com.zsmartsystems.zigbee.zcl.protocol.ZclDataType
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.*
import java.util.concurrent.Future

/**
 * ZigBee command line console is an example usage of the ZigBee console.
 *
 * Once refactoring is complete and all commands are migrated to the new format, this class will be deleted and the
 * remaining methods moved into the main class.
 *
 * @author Tommi S.E. Laukkanen
 * @author Chris Jackson
 */
class ZigBeeConsole
/**
 * Constructor which configures ZigBee API and constructs commands.
 *
 * @param dongle the [ZigBeeTransportTransmit]
 * @param transportCommands list of [ZigBeeConsoleCommand] to send to the transport
 */
(private val networkManager: ZigBeeNetworkManager,
 private val dongle: ZigBeeTransportTransmit,
 transportCommands: List<Class<out ZigBeeConsoleCommand>>) {
    /**
     * The main thread.
     */
    private var mainThread: Thread? = null

    /**
     * The flag reflecting that shutdown is in process.
     */
    private var shutdown = false

    /**
     * Whether to print attribute reports.
     */
    private var printAttributeReports = false

    /**
     * Map of registered commands and their implementations.
     */
    private val commands = TreeMap<String, ConsoleCommand>()
    private val newCommands = TreeMap<String, ZigBeeConsoleCommand>()

    init {

        // Add the extensions to the network
        networkManager.addExtension(ZigBeeIasCieExtension())
        networkManager.addExtension(ZigBeeOtaUpgradeExtension())
        networkManager.addExtension(ZigBeeBasicServerExtension())

        val discoveryExtension = ZigBeeDiscoveryExtension()
        discoveryExtension.updatePeriod = 60
        networkManager.addExtension(discoveryExtension)

        createCommands(newCommands, transportCommands)

        commands["groupadd"] = GroupAddCommand()
        commands["groupremove"] = GroupRemoveCommand()
        commands["grouplist"] = GroupListCommand()

        commands["membershipadd"] = MembershipAddCommand()
        commands["membershipremove"] = MembershipRemoveCommand()
        commands["membershipview"] = MembershipViewCommand()
        commands["membershiplist"] = MembershipListCommand()

        commands["quit"] = QuitCommand()
        commands["help"] = HelpCommand()
        commands["descriptor"] = SetDescriptorCommand()
        commands["on"] = OnCommand()
        commands["off"] = OffCommand()
        commands["color"] = ColorCommand()
        commands["level"] = LevelCommand()
        commands["listen"] = ListenCommand()
        commands["unlisten"] = UnlistenCommand()

        commands["ota"] = OtaCommand()
        commands["otafile"] = OtaFileCommand()

        commands["lqi"] = LqiCommand()
        commands["enroll"] = EnrollCommand()

        commands["firmware"] = FirmwareCommand()

        commands["supportedcluster"] = SupportedClusterCommand()
        commands["trustcentre"] = TrustCentreCommand()

        commands["rediscover"] = RediscoverCommand()

        commands["stress"] = StressCommand()

        newCommands["nodes"] = ZigBeeConsoleNodeListCommand()
        newCommands["endpoint"] = ZigBeeConsoleDescribeEndpointCommand()
        newCommands["node"] = ZigBeeConsoleDescribeNodeCommand()
        newCommands["bind"] = ZigBeeConsoleBindCommand()
        newCommands["unbind"] = ZigBeeConsoleUnbindCommand()
        newCommands["bindtable"] = ZigBeeConsoleBindingTableCommand()

        newCommands["read"] = ZigBeeConsoleAttributeReadCommand()
        newCommands["write"] = ZigBeeConsoleAttributeWriteCommand()

        newCommands["attsupported"] = ZigBeeConsoleAttributeSupportedCommand()
        newCommands["cmdsupported"] = ZigBeeConsoleCommandsSupportedCommand()

        newCommands["info"] = ZigBeeConsoleDeviceInformationCommand()
        newCommands["join"] = ZigBeeConsoleNetworkJoinCommand()
        newCommands["leave"] = ZigBeeConsoleNetworkLeaveCommand()

        newCommands["reporting"] = ZigBeeConsoleReportingConfigCommand()
        newCommands["subscribe"] = ZigBeeConsoleReportingSubscribeCommand()
        newCommands["unsubscribe"] = ZigBeeConsoleReportingUnsubscribeCommand()

        newCommands["installkey"] = ZigBeeConsoleInstallKeyCommand()
        newCommands["linkkey"] = ZigBeeConsoleLinkKeyCommand()

        newCommands["netstart"] = ZigBeeConsoleNetworkStartCommand()
        newCommands["netbackup"] = ZigBeeConsoleNetworkBackupCommand()
        newCommands["discovery"] = ZigBeeConsoleNetworkDiscoveryCommand()

        newCommands["otaupgrade"] = ZigBeeConsoleOtaUpgradeCommand()
        newCommands["channel"] = ZigBeeConsoleChannelCommand()


        networkManager.addNetworkStateListener { state -> print("ZigBee network state updated to $state", System.out) }

        networkManager.addNetworkNodeListener(object : ZigBeeNetworkNodeListener {
            override fun nodeAdded(node: ZigBeeNode) {
                print("Node Added $node", System.out)
            }

            override fun nodeUpdated(node: ZigBeeNode) {
                print("Node Updated $node", System.out)
            }

            override fun nodeRemoved(node: ZigBeeNode) {
                print("Node Removed $node", System.out)
            }
        })

        networkManager.addCommandListener { command ->
            if (printAttributeReports && command is ReportAttributesCommand) {
                print("Received: $command", System.out)
            }
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            shutdown = true
            try {
                System.`in`.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            try {
                mainThread!!.interrupt()
                mainThread!!.join()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        })
    }

    private fun createCommands(commands: MutableMap<String, ZigBeeConsoleCommand>,
                               transportCommands: List<Class<out ZigBeeConsoleCommand>>) {
        for (commandClass in transportCommands) {
            try {
                val command = commandClass.newInstance()
                commands[command.command] = command
            } catch (e: InstantiationException) {
                e.printStackTrace()
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            } catch (e: SecurityException) {
                e.printStackTrace()
            }

        }
    }

    /**
     * Starts this console application
     */
    fun start() {
        mainThread = Thread.currentThread()
        print("ZigBee API starting up...")

        print("ZigBee console ready.", System.out)

        print("PAN ID          = " + networkManager.zigBeePanId, System.out)
        print("Extended PAN ID = " + networkManager.zigBeeExtendedPanId, System.out)
        print("Channel         = " + networkManager.zigBeeChannel, System.out)

        var inputLine: String?

        while (true) {
            inputLine = readLine()
            if (shutdown || inputLine == null) break
            processInputLine(inputLine, System.out)
        }

        networkManager.shutdown()
    }

    /**
     * Processes text input line.
     *
     * @param inputLine the input line
     * @param out the output stream
     */
    fun processInputLine(inputLine: String, out: PrintStream) {
        if (inputLine.isEmpty()) return

        val args = inputLine.replace("\\s+".toRegex(), " ").split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        processArgs(args, out)
    }

    /**
     * Processes input arguments.
     *
     * @param args the input arguments
     * @param out the output stream
     */
    fun processArgs(args: Array<String>, out: PrintStream) {
        try {
            // if (commands.containsKey(args[0])) {
            executeCommand(networkManager, args, out)
            // } else {
            // print("Uknown command. Use 'help' command to list available commands.", out);
            // }
        } catch (e: Exception) {
            print("Exception in command execution: ", out)
            e.printStackTrace(out)
        }

    }

    /**
     * Executes command.
     *
     * @param networkManager the [ZigBeeNetworkManager]
     * @param args the arguments including the command
     * @param out the output stream
     */
    private fun executeCommand(networkManager: ZigBeeNetworkManager, args: Array<String>, out: PrintStream) {
        val consoleCommand = commands[args[0].toLowerCase()]
        if (consoleCommand != null) {
            try {
                consoleCommand.process(args, out)
            } catch (e: Exception) {
                out.println("Error executing command: $e")
                e.printStackTrace(out)
            }

            return
        }

        val newCommand = newCommands[args[0].toLowerCase()]
        if (newCommand != null) {
            try {
                newCommand.process(networkManager, args, out)
            } catch (e: IllegalArgumentException) {
                out.println("Error executing command: " + e.message)
                out.println(newCommand.command + " " + newCommand.syntax)
            } catch (e: IllegalStateException) {
                out.println("Error executing command: " + e.message)
                out.println(newCommand.command + " " + newCommand.syntax)
            } catch (e: Exception) {
                out.println("Error executing command: $e")
                e.printStackTrace(out)
            }

            return
        }

        print("Uknown command. Use 'help' command to list available commands.", out)
    }

    /**
     * Prints line to console.
     *
     * @param line the line
     */
    private fun print(line: String, out: PrintStream) {
        out.println("\r" + line)
        // if (out == System.out) {
        // System.out.print("\r> ");
        // }
    }

    /**
     * Reads line from console.
     *
     * @return line readLine from console or null if exception occurred.
     */
    private fun readLine(): String? {
        print("\r> ")
        return try {
            val bufferRead = BufferedReader(InputStreamReader(System.`in`))
            bufferRead.readLine()
        } catch (e: IOException) {
            null
        }

    }

    /**
     * Gets destination by device identifier or group ID.
     *
     * @param destinationIdentifier the device identifier or group ID
     * @return the device
     */
    private fun getDestination(destinationIdentifier: String,
                               out: PrintStream): ZigBeeAddress? {
        val device = getDevice(destinationIdentifier)

        if (device != null) {
            return device.endpointAddress
        }

        try {
            for (group in networkManager.groups) {
                if (destinationIdentifier == group.label) {
                    return group
                }
            }
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
    private fun getDevice(deviceIdentifier: String): ZigBeeEndpoint? {
        for (node in networkManager.nodes) {
            for (endpoint in node.endpoints) {
                if (deviceIdentifier == node.networkAddress.toString() + "/" + endpoint.endpointId) {
                    return endpoint
                }
            }
        }
        return null
    }

    /**
     * Interface for console commands.
     */
    private interface ConsoleCommand {
        /**
         * Get command description.
         *
         * @return the command description
         */
        val description: String

        /**
         * Get command syntax.
         *
         * @return the command syntax
         */
        val syntax: String

        /**
         * Processes console command.
         *
         * @param args the command arguments
         * @param out the output PrintStream
         * @return true if command syntax was correct.
         */
        @Throws(Exception::class)
        fun process(args: Array<String>, out: PrintStream): Boolean
    }

    /**
     * Quits console.
     */
    private inner class QuitCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Quits console."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "quit"

        /**
         * {@inheritDoc}
         */
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            shutdown = true
            return true
        }
    }

    /**
     * Prints help on console.
     */
    private inner class HelpCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "View command help."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "help [command]"

        /**
         * {@inheritDoc}
         */
        override fun process(args: Array<String>, out: PrintStream): Boolean {

            if (args.size == 2) {
                if (commands.containsKey(args[1])) {
                    val command = commands[args[1]]
                    print(command!!.description, out)
                    print("", out)
                    print("Syntax: " + command.syntax, out)
                } else {
                    return false
                }
            } else if (args.size == 1) {
                val commandList = ArrayList(commands.keys)
                Collections.sort(commandList)
                print("Commands:", out)
                for (command in commands.keys) {
                    print(command + " - " + commands[command]!!.description, out)
                }
                for (command in newCommands.keys) {
                    print(command + " - " + newCommands[command]!!.description, out)
                }
            } else {
                return false
            }

            return true
        }
    }

    /**
     * Lists groups in gateway network state.
     */
    private inner class GroupListCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Lists groups in gateway network state."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "grouplist"

        /**
         * {@inheritDoc}
         */
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            //            final List<ZigBeeGroupAddress> groups = networkManager.getGroups();
            //            for (final ZigBeeGroupAddress group : groups) {
            //                print(StringUtils.leftPad(Integer.toString(group.getGroupId()), 10) + "      " + group.getLabel(), out);
            //            }
            return true
        }
    }

    /**
     * Switches a device on.
     */
    private inner class OnCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Switches device on."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "on DEVICEID/DEVICELABEL/GROUPID"

        /**
         * {@inheritDoc}
         */
        @Throws(Exception::class)
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            if (args.size != 2) {
                return false
            }

            val destination = getDestination(args[1], out) ?: return false

            val response = on(destination)!!.get()
            return defaultResponseProcessing(response, out)
        }
    }

    /**
     * Switches a device off.
     */
    private inner class OffCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Switches device off."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "off DEVICEID/DEVICELABEL/GROUPID"

        /**
         * {@inheritDoc}
         */
        @Throws(Exception::class)
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            if (args.size != 2) {
                return false
            }

            val destination = getDestination(args[1], out) ?: return false

            val response = off(destination)!!.get()
            return defaultResponseProcessing(response, out)
        }
    }

    /**
     * Changes a light color on device.
     */
    private inner class ColorCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Changes light color."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "color DEVICEID RED GREEN BLUE"

        /**
         * {@inheritDoc}
         */
        @Throws(Exception::class)
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            if (args.size != 5) {
                return false
            }

            val destination = getDestination(args[1], out) ?: return false

            val red: Float
            try {
                red = java.lang.Float.parseFloat(args[2])
            } catch (e: NumberFormatException) {
                return false
            }

            val green: Float
            try {
                green = java.lang.Float.parseFloat(args[3])
            } catch (e: NumberFormatException) {
                return false
            }

            val blue: Float
            try {
                blue = java.lang.Float.parseFloat(args[4])
            } catch (e: NumberFormatException) {
                return false
            }

            val response = color(destination, red.toDouble(), green.toDouble(), blue.toDouble(), 1.0)!!.get()
            return defaultResponseProcessing(response, out)
        }
    }

    /**
     * Adds group to gateway network state. Does not affect actual ZigBee network.
     */
    private inner class GroupAddCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Adds group to gateway network state."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "groupadd GROUPID GROUPLABEL"

        /**
         * {@inheritDoc}
         */
        @Throws(Exception::class)
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            if (args.size != 3) {
                return false
            }

            val groupId: Int
            try {
                groupId = Integer.parseInt(args[1])
            } catch (e: NumberFormatException) {
                return false
            }

            val label = args[2]
            val group = ZigBeeGroupAddress()
            group.label = label
            networkManager.addGroup(group)

            return true
        }
    }

    /**
     * Removes group from network state but does not affect actual ZigBeet network.
     */
    private inner class GroupRemoveCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Removes group from gateway network state."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "groupremove GROUP"

        /**
         * {@inheritDoc}
         */
        @Throws(Exception::class)
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            if (args.size != 2) {
                return false
            }

            val destination = (getDestination(args[1], out) ?: return false) as? ZigBeeGroupAddress
                    ?: return false

            networkManager.removeGroup(destination.groupId)

            return true
        }
    }

    /**
     * Sets device user descriptor.
     */
    private inner class SetDescriptorCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Sets device user descriptor to 0-16 US-ASCII character string."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "descriptor DEVICEID DEVICELABEL"

        /**
         * {@inheritDoc}
         */
        @Throws(Exception::class)
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            if (args.size != 3) {
                return false
            }

            val device = getDevice(args[1]) ?: return false

            val label = args[2]

            return false
            // final CommandResult response = describe(device, label).get();
            // return defaultResponseProcessing(response, out);
        }
    }

    /**
     * Changes a device level for example lamp brightness.
     */
    private inner class LevelCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Changes device level for example lamp brightness, where LEVEL is between 0 and 1."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "level DEVICEID LEVEL [RATE]"

        /**
         * {@inheritDoc}
         */
        @Throws(Exception::class)
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            if (args.size < 3) {
                return false
            }

            val destination = getDestination(args[1], out) ?: return false

            val level: Float
            try {
                level = java.lang.Float.parseFloat(args[2])
            } catch (e: NumberFormatException) {
                return false
            }

            var time = 1.0.toFloat()
            if (args.size == 4) {
                try {
                    time = java.lang.Float.parseFloat(args[3])
                } catch (e: NumberFormatException) {
                    return false
                }

            }

            val response = level(destination, level.toDouble(), time.toDouble())!!.get()
            return defaultResponseProcessing(response, out)
        }
    }

    /**
     * Starts listening to reports of given attribute.
     */
    private inner class ListenCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Listen to attribute reports."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "listen"

        /**
         * {@inheritDoc}
         */
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            if (args.size != 1) {
                return false
            }

            printAttributeReports = true

            out.println("Printing received attribute reports.")

            return true
        }
    }

    /**
     * Unlisten from reports of given attribute.
     */
    private inner class UnlistenCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Unlisten from attribute reports."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "unlisten"

        /**
         * {@inheritDoc}
         */
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            if (args.size != 1) {
                return false
            }

            printAttributeReports = false

            out.println("Ignoring received attribute reports.")

            return true
        }
    }

    /**
     * Subscribes to reports of given attribute.
     */
    private inner class SubscribeCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Subscribe to attribute reports."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "subscribe [DEVICE] [CLUSTER] [ATTRIBUTE] [MIN-INTERVAL] [MAX-INTERVAL] [REPORTABLE-CHANGE]"

        /**
         * {@inheritDoc}
         */
        @Throws(Exception::class)
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            if (args.size != 6 && args.size != 7) {
                return false
            }

            val device = getDevice(args[1])
            if (device == null) {
                print("Device not found.", out)
                return false
            }

            val clusterId: Int
            try {
                clusterId = Integer.parseInt(args[2])
            } catch (e: NumberFormatException) {
                return false
            }

            val attributeId: Int
            try {
                attributeId = Integer.parseInt(args[3])
            } catch (e: NumberFormatException) {
                return false
            }

            val minInterval: Int
            try {
                minInterval = Integer.parseInt(args[4])
            } catch (e: NumberFormatException) {
                return false
            }

            val maxInterval: Int
            try {
                maxInterval = Integer.parseInt(args[5])
            } catch (e: NumberFormatException) {
                return false
            }

            val cluster = device.getCluster(clusterId)
            val attribute = cluster.getAttribute(attributeId)
            var reportableChange: Any? = null
            if (args.size > 6) {
                reportableChange = parseValue(args[6], attribute.dataType)
            }

            val zclAttribute = cluster.getAttribute(attributeId)
            if (zclAttribute == null) {
                out.println("Attribute not known.")
                return false
            }

            val result = cluster.setReporting(zclAttribute, minInterval, maxInterval, reportableChange)
                    .get()
            return if (result.isSuccess) {
                val response = result.getResponse<ConfigureReportingResponse>()
                val statusCode = response.records[0].status
                if (statusCode == ZclStatus.SUCCESS) {
                    out.println("Attribute value configure reporting success.")
                } else {
                    out.println("Attribute value configure reporting error: $statusCode")
                }
                true
            } else {
                out.println("Error executing command: $result")
                true
            }
        }
    }

    /**
     * Unsubscribes from reports of given attribute.
     */
    private inner class UnsubscribeCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Unsubscribe from attribute reports."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "unsubscribe [DEVICE] [CLUSTER] [ATTRIBUTE] [REPORTABLE-CHANGE]"

        /**
         * {@inheritDoc}
         */
        @Throws(Exception::class)
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            if (args.size != 4 && args.size != 5) {
                return false
            }

            val device = getDevice(args[1])
            val clusterId: Int
            try {
                clusterId = Integer.parseInt(args[2])
            } catch (e: NumberFormatException) {
                return false
            }

            val attributeId: Int
            try {
                attributeId = Integer.parseInt(args[3])
            } catch (e: NumberFormatException) {
                return false
            }

            val cluster = device!!.getCluster(clusterId)
            val attribute = cluster.getAttribute(attributeId)
            var reportableChange: Any? = null
            if (args.size > 4) {
                reportableChange = parseValue(args[4], attribute.dataType)
            }

            val zclAttribute = cluster.getAttribute(attributeId)
            if (zclAttribute == null) {
                out.println("Attribute not known.")
                return false
            }

            val result = cluster.setReporting(zclAttribute, 0, 0xffff, reportableChange).get()
            return if (result.isSuccess) {
                val response = result.getResponse<ConfigureReportingResponse>()
                val statusCode = response.records[0].status
                if (statusCode == ZclStatus.SUCCESS) {
                    out.println("Attribute value configure reporting success.")
                } else {
                    out.println("Attribute value configure reporting error: $statusCode")
                }
                true
            } else {
                out.println("Error executing command: $result")
                true
            }

        }
    }

    /**
     * Support for OTA server.
     */
    private inner class OtaCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Upgrade the firmware of a node using the OTA server."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "ota [ENDPOINT] [COMPLETE | FILE]"

        /**
         * {@inheritDoc}
         */
        @Throws(Exception::class)
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            if (args.size != 3) {
                return false
            }

            val endpoint = getDevice(args[1])
            if (endpoint == null) {
                print("Endpoint not found.", out)
                return false
            }

            // Check if the OTA server is already set
            var otaServer: ZclOtaUpgradeServer? = endpoint
                    .getApplication(ZclOtaUpgradeCluster.CLUSTER_ID) as ZclOtaUpgradeServer
            if (otaServer == null) {
                // Create and add the server
                otaServer = ZclOtaUpgradeServer()

                endpoint.addApplication(otaServer)

                otaServer.addListener { status, percent -> print("OTA status callback: $status, percent=$percent", out) }
            }

            if (args[2].toLowerCase() == "complete") {
                otaServer.completeUpgrade()
            } else {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false

                val file = FileSystems.getDefault().getPath("./", args[2])
                val fileData = Files.readAllBytes(file)
                val otaFile = ZigBeeOtaFile(fileData)
                print("OTA File: $otaFile", out)

                otaServer.setFirmware(otaFile)
            }
            return true
        }
    }

    /**
     * Support for OTA server.
     */
    private inner class OtaFileCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Dump information about an OTA file."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "ota [FILE]"

        /**
         * {@inheritDoc}
         */
        @Throws(Exception::class)
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            if (args.size != 2) return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false

            val file = FileSystems.getDefault().getPath("./", args[1])
            val fileData = Files.readAllBytes(file)

            val otaFile = ZigBeeOtaFile(fileData)
            print("OTA File: $otaFile", out)
            return true
        }
    }

    /**
     * Writes an attribute to a device.
     */
    private inner class LqiCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "List LQI neighbours list."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "lqi"

        /**
         * {@inheritDoc}
         */
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            // ZigBeeDiscoveryManager discoveryMan = getZigBeeDiscoveryManager();
            // NetworkNeighbourLinks neighbors = discoveryMan.getLinkQualityInfo();
            // final List<ZigBeeNode> nodes = getNodes();
            // for (int i = 0; i < nodes.size(); i++) {
            // final ZigBeeNode src = nodes.get(i);
            //
            // for (int j = 0; j < nodes.size(); j++) {
            // final ZigBeeNode dst = nodes.get(j);
            // int lqiLast = neighbors.getLast(src.getNetworkAddress(), dst.getNetworkAddress());
            // if(lqiLast != -1) {
            // print("Node #" + src.getNetworkAddress() + " receives node #" + dst.getNetworkAddress() +
            // " with LQI " + lqiLast + " (" +
            // neighbors.getMin(src.getNetworkAddress(), dst.getNetworkAddress()) + "/" +
            // neighbors.getAvg(src.getNetworkAddress(), dst.getNetworkAddress()) + "/" +
            // neighbors.getMax(src.getNetworkAddress(), dst.getNetworkAddress()) + ")", out
            // );
            // }
            // }
            //// }
            // return true;

            throw UnsupportedOperationException()
        }
    }

    /**
     * Enrolls IAS Zone device to this CIE device by setting own address as CIE address to the
     * IAS Zone device.
     */
    private inner class EnrollCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Enrolls IAS Zone device to this CIE device by setting own address as CIE address to the " + " IAS Zone device."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "enroll DEVICEID"

        /**
         * {@inheritDoc}
         */
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            if (args.size != 2) {
                return false
            }

            val device = getDevice(args[1]) ?: return false

            throw UnsupportedOperationException()
        }
    }

    /**
     * Adds group membership to device.
     */
    private inner class MembershipAddCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Adds group membership to device."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "membershipadd [DEVICE] [GROUPID] [GROUPNAME]"

        /**
         * {@inheritDoc}
         */
        @Throws(Exception::class)
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            if (args.size != 4) {
                return false
            }

            val device = getDevice(args[1])
            if (device == null) {
                print("Device not found.", out)
                return false
            }

            val groupId: Int
            try {
                groupId = Integer.parseInt(args[2])
            } catch (e: NumberFormatException) {
                return false
            }

            val groupName = args[3]

            val result = addMembership(device, groupId, groupName).get()

            return defaultResponseProcessing(result, out)

        }
    }

    /**
     * Removes device group membership from device.
     */
    private inner class MembershipRemoveCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Removes group membership from device."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "membershipremove [DEVICE] [GROUPID]"

        /**
         * {@inheritDoc}
         */
        @Throws(Exception::class)
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            if (args.size != 3) {
                return false
            }

            val device = getDevice(args[1])
            if (device == null) {
                print("Device not found.", out)
                return false
            }

            val groupId: Int
            try {
                groupId = Integer.parseInt(args[2])
            } catch (e: NumberFormatException) {
                return false
            }

            val result = removeMembership(device, groupId).get()

            return defaultResponseProcessing(result, out)
        }
    }

    /**
     * Views group name from device group membership.
     */
    private inner class MembershipViewCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Views group name from device group membership."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "membershipview [DEVICE] [GROUPID]"

        /**
         * {@inheritDoc}
         */
        @Throws(Exception::class)
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            if (args.size != 3) {
                return false
            }

            val device = getDevice(args[1])
            if (device == null) {
                print("Device not found.", out)
                return false
            }

            val groupId: Int
            try {
                groupId = Integer.parseInt(args[2])
            } catch (e: NumberFormatException) {
                return false
            }

            val result = viewMembership(device, groupId).get()

            return if (result.isSuccess) {
                val response = result.getResponse<ViewGroupResponse>()
                val statusCode = response.status!!
                if (statusCode == 0) {
                    out.println("Group name: " + response.groupName)
                } else {
                    val status = ZclStatus.getStatus(statusCode.toByte().toInt())
                    out.println("Error reading group name: $status")
                }
                true
            } else {
                out.println("Error executing command: $result")
                true
            }
        }
    }

    /**
     * Lists group memberships from device.
     */
    private inner class MembershipListCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Lists group memberships from device."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "membershiplist [DEVICE]"

        /**
         * {@inheritDoc}
         */
        @Throws(Exception::class)
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            if (args.size != 2) {
                return false
            }

            val device = getDevice(args[1])
            if (device == null) {
                print("Device not found.", out)
                return false
            }

            val result = getGroupMemberships(device).get()
            return if (result.isSuccess) {
                val response = result.getResponse<GetGroupMembershipResponse>()
                out.print("Member of groups:")
                for (value in response.groupList) {
                    out.print(' ')
                    out.print(value)
                }
                out.println()
                true
            } else {
                out.println("Error executing command: $result")
                true
            }
        }
    }

    /**
     * Trust centre configuration.
     */
    private inner class TrustCentreCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Configures the trust centre."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "trustcentre [MODE|KEY] [MODE / KEY]"

        /**
         * {@inheritDoc}
         */
        @Throws(Exception::class)
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            if (args.size < 3) {
                return false
            }
            val config = TransportConfig()
            when (args[1].toLowerCase()) {
                "mode" -> config.addOption(TransportConfigOption.TRUST_CENTRE_JOIN_MODE,
                        TrustCentreJoinMode.valueOf(args[2].toUpperCase()))
                "key" -> {
                    var key = ""
                    for (cnt in 0..15) {
                        key += args[cnt + 2]
                    }
                    config.addOption(TransportConfigOption.TRUST_CENTRE_LINK_KEY, ZigBeeKey(key))
                }

                else -> return false
            }

            val option = config.options.iterator().next()
            dongle.updateTransportConfig(config)
            print("Trust Centre configuration for " + option + " returned " + config.getResult(option), out)
            return true
        }
    }

    /**
     * Locks door.
     */
    private inner class SupportedClusterCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Adds a cluster to the list of supported clusters"

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "supportedcluster CLUSTER"

        /**
         * {@inheritDoc}
         */
        @Throws(Exception::class)
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            if (args.size != 2) {
                return false
            }

            var clusterId = 0
            if (args[1].startsWith("0x")) {
                clusterId = Integer.parseInt(args[1].substring(2), 16)
            } else {
                clusterId = Integer.parseInt(args[1])
            }

            networkManager.addSupportedCluster(clusterId)

            print("Added cluster " + String.format("0x%X", clusterId) + " to match descriptor list.", out)

            return true
        }
    }

    /**
     * Dongle firmware update command.
     */
    private inner class FirmwareCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Updates the dongle firmware"

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "firmware [VERSION | CANCEL | FILE]"

        /**
         * {@inheritDoc}
         */
        @Throws(Exception::class)
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            if (args.size != 2) {
                return false
            }

            if (dongle !is ZigBeeTransportFirmwareUpdate) {
                print("Dongle does not implement firmware updates.", out)
                return false
            }
            val firmwareUpdate = dongle as ZigBeeTransportFirmwareUpdate

            if (args[1].toLowerCase() == "version") {
                print("Dongle firmware version is currently " + firmwareUpdate.firmwareVersion, out)
                return true
            }

            if (args[1].toLowerCase() == "cancel") {
                print("Cancelling dongle firmware update!", out)
                firmwareUpdate.cancelUpdateFirmware()
                return true
            }

            networkManager.shutdown()

            val firmwareFile = File(args[1])
            val firmwareStream: InputStream
            try {
                firmwareStream = FileInputStream(firmwareFile)
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                return false
            }

            firmwareUpdate.updateFirmware(firmwareStream) { status -> print("Dongle firmware status: $status.", out) }

            out.println("Starting dongle firmware update...")
            return true
        }
    }

    /**
     * Rediscover a node from its IEEE address.
     */
    private inner class RediscoverCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Rediscover a node from its IEEE address."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "rediscover IEEEADDRESS"

        /**
         * {@inheritDoc}
         */
        @Throws(Exception::class)
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            if (args.size != 2) {
                return false
            }

            val address = IeeeAddress(args[1])

            print("Sending rediscovery request for address $address", out)
            networkManager.rediscoverNode(address)
            return true
        }
    }

    /**
     * Stress the system by sending a command to a node
     */
    private inner class StressCommand : ConsoleCommand {
        /**
         * {@inheritDoc}
         */
        override val description: String
            get() = "Stress test. Note after sending this command you will need to stop the console."

        /**
         * {@inheritDoc}
         */
        override val syntax: String
            get() = "stress NODEID"

        /**
         * {@inheritDoc}
         */
        @Throws(Exception::class)
        override fun process(args: Array<String>, out: PrintStream): Boolean {
            if (args.size != 2) {
                return false
            }

            val endpoint = getDevice(args[1])
            if (endpoint == null) {
                print("Endpoint not found.", out)
                return false
            }

            Thread {
                var cnt = 0
                while (true) {
                    print("STRESSING 1 CNT: " + cnt++, out)
                    val cluster = endpoint
                            .getInputCluster(ZclOnOffCluster.CLUSTER_ID) as ZclOnOffCluster
                    cluster.onCommand()
                    try {
                        Thread.sleep(167)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }

                }
            }.start()

            Thread {
                var cnt = 0
                while (true) {
                    print("STRESSING 2 CNT: " + cnt++, out)
                    val cluster = endpoint
                            .getInputCluster(ZclOnOffCluster.CLUSTER_ID) as ZclOnOffCluster
                    cluster.onCommand()
                    try {
                        Thread.sleep(107)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }

                }
            }.start()

            Thread {
                var cnt = 0
                while (true) {
                    print("STRESSING 3 CNT: " + cnt++, out)
                    val cluster = endpoint
                            .getInputCluster(ZclOnOffCluster.CLUSTER_ID) as ZclOnOffCluster
                    cluster.onCommand()
                    try {
                        Thread.sleep(131)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }

                }
            }.start()

            Thread {
                var cnt = 0
                while (true) {
                    print("STRESSING 4 CNT: " + cnt++, out)
                    val cluster = endpoint
                            .getInputCluster(ZclOnOffCluster.CLUSTER_ID) as ZclOnOffCluster
                    cluster.onCommand()
                    try {
                        Thread.sleep(187)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }

                }
            }.start()

            return true
        }
    }

    /**
     * Default processing for command result.
     *
     * @param result the command result
     * @param out the output
     * @return TRUE if result is success
     */
    private fun defaultResponseProcessing(result: CommandResult, out: PrintStream): Boolean {
        return if (result.isSuccess) {
            out.println("Success response received.")
            true
        } else {
            out.println("Error executing command: $result")
            true
        }
    }

    /**
     * Parses string value to Object.
     *
     * @param stringValue the string value
     * @param zclDataType the ZigBee type
     * @return the value Object
     */
    private fun parseValue(stringValue: String, zclDataType: ZclDataType): Any? {
        var value: Any? = null
        when (zclDataType) {
            ZclDataType.BITMAP_16_BIT -> value = Integer.parseInt(stringValue)
            // case BITMAP_24_BIT:
            // value = Integer.parseInt(stringValue);
            // break;
            // case BITMAP_32_BIT:
            // value = Integer.parseInt(stringValue);
            // break;
            ZclDataType.BITMAP_8_BIT -> value = Integer.parseInt(stringValue)
            ZclDataType.BOOLEAN -> value = java.lang.Boolean.parseBoolean(stringValue)
            ZclDataType.CHARACTER_STRING -> value = stringValue
            // case DATA_16_BIT:
            // value = Integer.parseInt(stringValue);
            // break;
            // case DATA_24_BIT:
            // value = Integer.parseInt(stringValue);
            // break;
            // case DATA_32_BIT:
            // value = Integer.parseInt(stringValue);
            // break;
            ZclDataType.DATA_8_BIT -> value = Integer.parseInt(stringValue)
            // case DOUBLE_PRECISION:
            // value = Double.parseDouble(stringValue);
            // break;
            ZclDataType.ENUMERATION_16_BIT -> value = Integer.parseInt(stringValue)
            ZclDataType.ENUMERATION_8_BIT -> value = Integer.parseInt(stringValue)
            ZclDataType.IEEE_ADDRESS -> value = IeeeAddress(stringValue)
            // case LONG_CHARACTER_STRING:
            // value = stringValue;
            // break;
            // case LONG_OCTET_STRING:
            // value = stringValue;
            // break;
            ZclDataType.OCTET_STRING -> value = stringValue
            // case SEMI_PRECISION:
            // throw new UnsupportedOperationException("SemiPrecision parsing not implemented");
            ZclDataType.SIGNED_8_BIT_INTEGER -> value = Integer.parseInt(stringValue)
            ZclDataType.SIGNED_16_BIT_INTEGER -> value = Integer.parseInt(stringValue)
            // case SIGNED_24_BIT_INTEGER:
            // value = Integer.parseInt(stringValue);
            // break;
            ZclDataType.SIGNED_32_BIT_INTEGER -> value = Integer.parseInt(stringValue)
            // case SINGLE_PRECISION:
            // throw new UnsupportedOperationException("SinglePrecision parsing not implemented");
            ZclDataType.UNSIGNED_8_BIT_INTEGER -> value = Integer.parseInt(stringValue)
            ZclDataType.UNSIGNED_16_BIT_INTEGER -> value = Integer.parseInt(stringValue)
            // case UNSIGNED_24_BIT_INTEGER:
            // value = Integer.parseInt(stringValue);
            // break;
            ZclDataType.UNSIGNED_32_BIT_INTEGER -> value = Integer.parseInt(stringValue)
            else -> {
            }
        }
        return value
    }


    fun on(destination: ZigBeeAddress): Future<CommandResult>? {
        if (destination !is ZigBeeEndpointAddress) {
            return null
        }
        val endpoint = networkManager.getNode(destination.address)
                .getEndpoint(destination.endpoint) ?: return null
        val cluster = endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID) as ZclOnOffCluster
        return cluster.onCommand()
    }

    /**
     * Switches destination off.
     *
     * @param destination the [ZigBeeAddress]
     * @return the command result future.
     */
    fun off(destination: ZigBeeAddress): Future<CommandResult>? {
        if (destination !is ZigBeeEndpointAddress) {
            return null
        }
        val endpoint = networkManager.getNode(destination.address)
                .getEndpoint(destination.endpoint) ?: return null
        val cluster = endpoint.getInputCluster(ZclOnOffCluster.CLUSTER_ID) as ZclOnOffCluster
        return cluster.offCommand()
    }

    /**
     * Colors device light.
     *
     * @param destination the [ZigBeeAddress]
     * @param red the red component [0..1]
     * @param green the green component [0..1]
     * @param blue the blue component [0..1]
     * @param time the in seconds
     * @return the command result future.
     */
    fun color(destination: ZigBeeAddress, red: Double, green: Double,
              blue: Double, time: Double): Future<CommandResult>? {

        val cie = Cie.rgb2cie(red, green, blue)

        var x = (cie.x * 65536).toInt()
        var y = (cie.y * 65536).toInt()
        if (x > 65279) {
            x = 65279
        }
        if (y > 65279) {
            y = 65279
        }

        if (destination !is ZigBeeEndpointAddress) {
            return null
        }
        val endpoint = networkManager.getNode(destination.address)
                .getEndpoint(destination.endpoint) ?: return null
        val cluster = endpoint
                .getInputCluster(ZclColorControlCluster.CLUSTER_ID) as ZclColorControlCluster
        return cluster.moveToColorCommand(x, y, (time * 10).toInt())
    }

    /**
     * Moves device level.
     *
     * @param destination the [ZigBeeAddress]
     * @param level the level
     * @param time the transition time
     * @return the command result future.
     */
    fun level(destination: ZigBeeAddress, level: Double, time: Double): Future<CommandResult>? {

        var l = (level * 254).toInt()
        if (l > 254) {
            l = 254
        }
        if (l < 0) {
            l = 0
        }

        if (destination !is ZigBeeEndpointAddress) {
            return null
        }
        val endpoint = networkManager.getNode(destination.address)
                .getEndpoint(destination.endpoint) ?: return null
        val cluster = endpoint
                .getInputCluster(ZclLevelControlCluster.CLUSTER_ID) as ZclLevelControlCluster
        return cluster.moveToLevelWithOnOffCommand(l, (time * 10).toInt())
    }

    /**
     * Adds group membership to device.
     *
     * @param device the device
     * @param groupId the group ID
     * @param groupName the group name
     * @return the command result future
     */
    fun addMembership(device: ZigBeeEndpoint, groupId: Int, groupName: String): Future<CommandResult> {
        val command = AddGroupCommand()
        command.groupId = groupId
        command.groupName = groupName

        command.destinationAddress = device.endpointAddress

        return networkManager.sendTransaction(command, ZclTransactionMatcher())
    }

    /**
     * Gets group memberships from device.
     *
     * @param device the device
     * @return the command result future
     */
    fun getGroupMemberships(device: ZigBeeEndpoint): Future<CommandResult> {
        val command = GetGroupMembershipCommand()

        command.groupCount = 0
        command.groupList = emptyList()
        command.destinationAddress = device.endpointAddress

        return networkManager.sendTransaction(command, ZclTransactionMatcher())
    }

    /**
     * Views group membership from device.
     *
     * @param device the device
     * @param groupId the group ID
     * @return the command result future
     */
    fun viewMembership(device: ZigBeeEndpoint, groupId: Int): Future<CommandResult> {
        val command = ViewGroupCommand()
        command.groupId = groupId

        command.destinationAddress = device.endpointAddress

        return networkManager.sendTransaction(command, ZclTransactionMatcher())
    }

    /**
     * Removes group membership from device.
     *
     * @param device the device
     * @param groupId the group ID
     * @return the command result future
     */
    fun removeMembership(device: ZigBeeEndpoint, groupId: Int): Future<CommandResult> {
        val command = RemoveGroupCommand()
        command.groupId = groupId

        command.destinationAddress = device.endpointAddress

        return networkManager.sendTransaction(command, ZclTransactionMatcher())
    }
}
