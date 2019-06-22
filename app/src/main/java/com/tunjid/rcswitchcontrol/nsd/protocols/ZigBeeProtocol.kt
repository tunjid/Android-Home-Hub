package com.tunjid.rcswitchcontrol.nsd.protocols

import android.content.Context.USB_SERVICE
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.HandlerThread
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.data.Payload
import com.tunjid.rcswitchcontrol.data.ZigBeeCommandArgs
import com.tunjid.rcswitchcontrol.data.ZigBeeCommandInfo
import com.tunjid.rcswitchcontrol.io.*
import com.zsmartsystems.zigbee.*
import com.zsmartsystems.zigbee.app.basic.ZigBeeBasicServerExtension
import com.zsmartsystems.zigbee.app.discovery.ZigBeeDiscoveryExtension
import com.zsmartsystems.zigbee.app.iasclient.ZigBeeIasCieExtension
import com.zsmartsystems.zigbee.app.otaserver.ZigBeeOtaUpgradeExtension
import com.zsmartsystems.zigbee.console.*
import com.zsmartsystems.zigbee.dongle.cc2531.ZigBeeDongleTiCc2531
import com.zsmartsystems.zigbee.security.ZigBeeKey
import com.zsmartsystems.zigbee.serialization.DefaultDeserializer
import com.zsmartsystems.zigbee.serialization.DefaultSerializer
import com.zsmartsystems.zigbee.transport.TransportConfig
import com.zsmartsystems.zigbee.transport.TransportConfigOption
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster
import java.io.OutputStream
import java.io.PrintStream
import java.io.PrintWriter


class ZigBeeProtocol(printWriter: PrintWriter) : CommsProtocol(printWriter) {

    private val thread = HandlerThread("ZigBee").apply { start() }
    private val handler = Handler(thread.looper)

    private val outStream = PrintStream(ConsoleOutputStream { post(it) }, true)

    private val dongle: ZigBeeDongleTiCc2531
    private val networkManager: ZigBeeNetworkManager
    private val availableCommands: Map<String, ZigBeeConsoleCommand> = mutableMapOf(
            "nodes" to ZigBeeConsoleNodeListCommand(),
            "endpoint" to ZigBeeConsoleDescribeEndpointCommand(),
            "node" to ZigBeeConsoleDescribeNodeCommand(),
            "bind" to ZigBeeConsoleBindCommand(),
            "unbind" to ZigBeeConsoleUnbindCommand(),
            "bindtable" to ZigBeeConsoleBindingTableCommand(),

            "read" to ZigBeeConsoleAttributeReadCommand(),
            "write" to ZigBeeConsoleAttributeWriteCommand(),

            "attsupported" to ZigBeeConsoleAttributeSupportedCommand(),
            "cmdsupported" to ZigBeeConsoleCommandsSupportedCommand(),

            "info" to ZigBeeConsoleDeviceInformationCommand(),
            "join" to ZigBeeConsoleNetworkJoinCommand(),
            "leave" to ZigBeeConsoleNetworkLeaveCommand(),

            "reporting" to ZigBeeConsoleReportingConfigCommand(),
            "subscribe" to ZigBeeConsoleReportingSubscribeCommand(),
            "unsubscribe" to ZigBeeConsoleReportingUnsubscribeCommand(),

            "installkey" to ZigBeeConsoleInstallKeyCommand(),
            "linkkey" to ZigBeeConsoleLinkKeyCommand(),

            "netstart" to ZigBeeConsoleNetworkStartCommand(),
            "netbackup" to ZigBeeConsoleNetworkBackupCommand(),
            "discovery" to ZigBeeConsoleNetworkDiscoveryCommand(),

            "otaupgrade" to ZigBeeConsoleOtaUpgradeCommand(),
            "channel" to ZigBeeConsoleChannelCommand(),

            "on" to OnCommand(),
            "off" to OffCommand(),
            "color" to ColorCommand(),
            "level" to LevelCommand()
    ).let { it["help"] = HelpCommand(it); it.toMap() }

    init {
        val manager: UsbManager = App.instance.getSystemService(USB_SERVICE) as UsbManager

        val customTable = ProbeTable()
        customTable.addProduct(1105, 5800, CdcAcmSerialDriver::class.java)

        val drivers = UsbSerialProber(customTable).findAllDrivers(manager)

        if (drivers.isEmpty()) throw IllegalArgumentException("No driver available")

        val driver = drivers[0]

        dongle = ZigBeeDongleTiCc2531(AndroidSerialPort(driver, 115200))
        networkManager = ZigBeeNetworkManager(dongle).apply {
            //            setNetworkDataStore(ZigBeeDataStore(dongleName))
            addExtension(ZigBeeIasCieExtension())
            addExtension(ZigBeeOtaUpgradeExtension())
            addExtension(ZigBeeBasicServerExtension())
            addExtension(ZigBeeDiscoveryExtension().apply { updatePeriod = 60 })

            setSerializer(DefaultSerializer::class.java, DefaultDeserializer::class.java)

            addNetworkStateListener { state -> post("ZigBee network state updated to $state") }

            addNetworkNodeListener(object : ZigBeeNetworkNodeListener {
                override fun nodeAdded(node: ZigBeeNode) = post("Node Added $node")

                override fun nodeUpdated(node: ZigBeeNode) = post("Node Updated $node")

                override fun nodeRemoved(node: ZigBeeNode) = post("Node Removed $node")
            })

            addCommandListener {}
        }

        handler.post(this::start)
    }

    override fun processInput(payload: Payload): Payload {
        val builder = Payload.builder()
        builder.setKey(javaClass.name).addCommand(RESET)

        when (val action = payload.action ?: "invalid command") {
            RESET -> reset()
            PING -> builder.setResponse(getString(R.string.zigbeeprotocol_ping))
            in availableCommands.keys -> availableCommands[action]?.apply {
                val data = payload.data
                val commandArgs = if (data == null) null else ZigBeeCommandArgs.deserialize(data)
                val needsCommandArgs: Boolean = (commandArgs == null || commandArgs.isInvalid) && syntax.isNotEmpty()

                when {
                    needsCommandArgs -> {
                        builder.setResponse(getString(R.string.zigbeeprotocol_enter_args, action))
                        builder.setData(ZigBeeCommandInfo(command, description, syntax, help).serialize())
                    }
                    else -> {
                        val args = commandArgs?.args ?: arrayOf(action)
                        builder.setResponse(getString(R.string.zigbeeprotocol_executing, args.commandString()))
                        execute(args)
                    }
                }

            }
            else -> builder.setResponse("Unrecognized command")
        }

        availableCommands.keys.forEach { builder.addCommand(it) }

        return builder.build()
    }

    private fun Array<String>.commandString() = joinToString(separator = " ")

    private fun start() {
        val resetNetwork = true
        val transportOptions = TransportConfig()

        // Initialise the network
        val initResponse = networkManager.initialize()

        if (initResponse != ZigBeeStatus.SUCCESS) {
            close()
            return
        }

        println("PAN ID          = " + networkManager.zigBeePanId)
        println("Extended PAN ID = " + networkManager.zigBeeExtendedPanId)
        println("Channel         = " + networkManager.zigBeeChannel)

        if (resetNetwork) reset()

        // Add the default ZigBeeAlliance09 HA link key

        transportOptions.addOption(TransportConfigOption.TRUST_CENTRE_LINK_KEY, ZigBeeKey(intArrayOf(0x5A, 0x69, 0x67, 0x42, 0x65, 0x65, 0x41, 0x6C, 0x6C, 0x69, 0x61, 0x6E, 0x63, 0x65, 0x30, 0x39)))
        // transportOptions.addOption(TransportConfigOption.TRUST_CENTRE_LINK_KEY, ZigBeeKey(new int[] { 0x41, 0x61,
        // 0x8F, 0xC0, 0xC8, 0x3B, 0x0E, 0x14, 0xA5, 0x89, 0x95, 0x4B, 0x16, 0xE3, 0x14, 0x66 }));

        dongle.updateTransportConfig(transportOptions)

        if (networkManager.startup(resetNetwork) !== ZigBeeStatus.SUCCESS) {
            println("ZigBee console starting up ... [FAIL]")
        } else {
            println("ZigBee console starting up ... [OK]")
        }

        networkManager.addSupportedCluster(ZclIasZoneCluster.CLUSTER_ID)

        dongle.setLedMode(1, false)
        dongle.setLedMode(2, false)
    }

    private fun execute(args: Array<String>) {
        try {
            executeCommand(networkManager, args)
        } catch (e: Exception) {
            post("Exception in executing command: ${args.commandString()}")
            e.printStackTrace()
        }
    }

    /**
     * Executes command.
     *
     * @param networkManager the [ZigBeeNetworkManager]
     * @param args the arguments including the command
     */
    private fun executeCommand(networkManager: ZigBeeNetworkManager, args: Array<String>) {
        val command = availableCommands[args[0].toLowerCase()]
                ?: return post("Unknown command. Use 'help' command to list available commands.")

        try {
            command.process(networkManager, args, outStream)
        } catch (e: IllegalArgumentException) {
            post(
                    "Error executing command: ${e.message}",
                    "${command.command} ${command.syntax}"
            )
        } catch (e: IllegalStateException) {
            post(
                    "Error executing command: " + e.message,
                    command.command + " " + command.syntax
            )
        } catch (e: Exception) {
            post("Error executing command: $e")
        }
    }

    private fun reset() {
        val nwkKey: ZigBeeKey = ZigBeeKey.createRandom()
        val linkKey = ZigBeeKey(intArrayOf(0x5A, 0x69, 0x67, 0x42, 0x65, 0x65, 0x41, 0x6C, 0x6C, 0x69, 0x61, 0x6E, 0x63, 0x65, 0x30, 0x39))
        val extendedPan = ExtendedPanId()
        val channel = 11
        val pan = 1

        val stringBuilder = StringBuilder().apply {
            append("*** Resetting network")
            append("  * Channel                = $channel")
            append("  * PAN ID                 = $pan")
            append("  * Extended PAN ID        = $extendedPan")
            append("  * Link Key               = $linkKey")
            if (nwkKey.hasOutgoingFrameCounter()) append("  * Link Key Frame Cnt     = " + linkKey.outgoingFrameCounter!!)
            append("  * Network Key            = $nwkKey")

            if (nwkKey.hasOutgoingFrameCounter()) append("  * Network Key Frame Cnt  = " + nwkKey.outgoingFrameCounter!!)
        }


        networkManager.zigBeeChannel = ZigBeeChannel.create(channel)
        networkManager.zigBeePanId = pan
        networkManager.zigBeeExtendedPanId = extendedPan
        networkManager.zigBeeNetworkKey = nwkKey
        networkManager.zigBeeLinkKey = linkKey

        post(stringBuilder.toString())
    }

    private fun post(vararg messages: String) {
        val builder = Payload.builder().apply {
            setKey(this@ZigBeeProtocol.javaClass.name)
            setResponse(StringBuilder().let {
                messages.forEach { message -> it.append(message); it.append("\n") }
                it.toString()
            })
            addCommand(RESET)
            availableCommands.keys.forEach { addCommand(it) }
        }

        handler.post { printWriter.println(builder.build().serialize()) }
    }

    override fun close() {
        thread.quitSafely()
        networkManager.shutdown()
    }

    class ConsoleOutputStream(private val consumer: (String) -> Unit) : OutputStream() {
        private val stringBuilder = StringBuilder()

        override fun write(b: Int) {
            this.stringBuilder.append(b.toChar())
        }

        override fun toString(): String = this.stringBuilder.toString()

        override fun flush() {
            super.flush()
            consumer.invoke(stringBuilder.toString())
            stringBuilder.setLength(0)
        }
    }
}