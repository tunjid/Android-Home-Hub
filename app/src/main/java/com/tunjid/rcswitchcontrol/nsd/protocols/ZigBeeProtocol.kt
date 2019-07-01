package com.tunjid.rcswitchcontrol.nsd.protocols

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.data.Payload
import com.tunjid.rcswitchcontrol.data.ZigBeeCommandArgs
import com.tunjid.rcswitchcontrol.data.ZigBeeCommandInfo
import com.tunjid.rcswitchcontrol.data.ZigBeeDevice
import com.tunjid.rcswitchcontrol.data.persistence.Converter.Companion.deserialize
import com.tunjid.rcswitchcontrol.data.persistence.Converter.Companion.serialize
import com.tunjid.rcswitchcontrol.data.persistence.Converter.Companion.serializeList
import com.tunjid.rcswitchcontrol.data.persistence.ZigBeeDataStore
import com.tunjid.rcswitchcontrol.io.AndroidSerialPort
import com.tunjid.rcswitchcontrol.io.ConsoleStream
import com.tunjid.rcswitchcontrol.zigbee.*
import com.zsmartsystems.zigbee.*
import com.zsmartsystems.zigbee.app.basic.ZigBeeBasicServerExtension
import com.zsmartsystems.zigbee.app.discovery.ZigBeeDiscoveryExtension
import com.zsmartsystems.zigbee.app.iasclient.ZigBeeIasCieExtension
import com.zsmartsystems.zigbee.app.otaserver.ZigBeeOtaUpgradeExtension
import com.zsmartsystems.zigbee.console.*
import com.zsmartsystems.zigbee.database.ZigBeeNodeDao
import com.zsmartsystems.zigbee.dongle.cc2531.ZigBeeDongleTiCc2531
import com.zsmartsystems.zigbee.security.ZigBeeKey
import com.zsmartsystems.zigbee.serialization.DefaultDeserializer
import com.zsmartsystems.zigbee.serialization.DefaultSerializer
import com.zsmartsystems.zigbee.transport.TransportConfig
import com.zsmartsystems.zigbee.transport.TransportConfigOption
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType
import io.reactivex.Flowable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.processors.PublishProcessor
import java.io.PrintWriter
import java.util.concurrent.TimeUnit


class ZigBeeProtocol(driver: UsbSerialDriver, printWriter: PrintWriter) : CommsProtocol(printWriter) {

    private val SAVED_DEVICES = getString(R.string.zigbeeprotocol_saved_devices)

    private val thread = HandlerThread("ZigBee").apply { start() }
    private val handler = Handler(thread.looper)

    private val disposable = CompositeDisposable()
    private val outputProcessor: PublishProcessor<String> = PublishProcessor.create()

    private val outStream = ConsoleStream { post(it) }

    private val dongle: ZigBeeDongleTiCc2531
    private val dataStore = ZigBeeDataStore("Home")
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
            "level" to LevelCommand(),
            "rediscover" to RediscoverCommand()

    ).let { it["help"] = HelpCommand(it); it.toMap() }

    init {
        dongle = ZigBeeDongleTiCc2531(AndroidSerialPort(driver, BAUD_RATE))
        networkManager = ZigBeeNetworkManager(dongle).apply {
            setNetworkDataStore(dataStore)
            addExtension(ZigBeeIasCieExtension())
            addExtension(ZigBeeOtaUpgradeExtension())
            addExtension(ZigBeeBasicServerExtension())
            addExtension(ZigBeeDiscoveryExtension().apply { updatePeriod = MESH_UPDATE_PERIOD })

            setSerializer(DefaultSerializer::class.java, DefaultDeserializer::class.java)

            addNetworkStateListener { state ->
                post("ZigBee network state updated to $state")
                if (ZigBeeNetworkState.ONLINE == state) formNetwork()
            }

            addNetworkNodeListener(object : ZigBeeNetworkNodeListener {
                override fun nodeAdded(node: ZigBeeNode) = post("Node Added $node")

                override fun nodeUpdated(node: ZigBeeNode) = post("Node Updated $node")

                override fun nodeRemoved(node: ZigBeeNode) = post("Node Removed $node")
            })

            addCommandListener {}
        }

        processOutput()
        handler.post(this::start)
    }

    override fun processInput(payload: Payload): Payload {
        val output = Payload(javaClass.name)
        output.addCommand(RESET)

        when (val action = payload.action ?: "invalid command") {
            RESET -> reset()
            FORM_NETWORK -> formNetwork()
            SAVED_DEVICES -> savedDevices()
            PING -> output.response = (getString(R.string.zigbeeprotocol_ping))
            in availableCommands.keys -> availableCommands[action]?.apply {
                val commandArgs = payload.data?.deserialize(ZigBeeCommandArgs::class)
                val needsCommandArgs: Boolean = (commandArgs == null || commandArgs.isInvalid) && syntax.isNotEmpty()

                when {
                    needsCommandArgs -> {
                        output.response = getString(R.string.zigbeeprotocol_enter_args, action)
                        output.data = ZigBeeCommandInfo(command, description, syntax, help).serialize()
                    }
                    else -> {
                        val args = commandArgs?.args ?: arrayOf(action)
                        output.response = getString(R.string.zigbeeprotocol_executing, args.commandString())
                        execute(args)
                    }
                }
            }
            else -> output.response = "Unrecognized command"
        }

        output.appendCommands()

        return output
    }

    /**
     * Buffers writes to the output stream because quick concurrent writes cause data loss to the client
     */
    private fun processOutput() {
        disposable.add(outputProcessor
                .onBackpressureBuffer()
                .concatMap { Flowable.just(it).delay(OUTPUT_BUFFER_RATE, TimeUnit.MILLISECONDS) }
                .subscribe({ if (thread.isAlive) handler.post { printWriter.println(it) } }, { it.printStackTrace(); processOutput() }))
    }

    private fun start() {
        val resetNetwork = dataStore.hasNoDevices
        val transportOptions = TransportConfig()

        // Initialise the network
        val initResponse = networkManager.initialize()

        if (initResponse != ZigBeeStatus.SUCCESS) return close()

        post("PAN ID          = " + networkManager.zigBeePanId)
        post("Extended PAN ID = " + networkManager.zigBeeExtendedPanId)
        post("Channel         = " + networkManager.zigBeeChannel)

        if (resetNetwork) reset()

        // Add the default ZigBeeAlliance09 HA link key

        transportOptions.addOption(TransportConfigOption.TRUST_CENTRE_LINK_KEY, ZigBeeKey(intArrayOf(0x5A, 0x69, 0x67, 0x42, 0x65, 0x65, 0x41, 0x6C, 0x6C, 0x69, 0x61, 0x6E, 0x63, 0x65, 0x30, 0x39)))
        // transportOptions.addOption(TransportConfigOption.TRUST_CENTRE_LINK_KEY, ZigBeeKey(new int[] { 0x41, 0x61,
        // 0x8F, 0xC0, 0xC8, 0x3B, 0x0E, 0x14, 0xA5, 0x89, 0x95, 0x4B, 0x16, 0xE3, 0x14, 0x66 }));

        dongle.updateTransportConfig(transportOptions)

        post(
                if (networkManager.startup(resetNetwork) !== ZigBeeStatus.SUCCESS) "ZigBee console starting up ... [FAIL]"
                else "ZigBee console starting up ... [OK]"
        )

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
        val extendedPan = ExtendedPanId("987654321")
        val channel = 11
        val pan = 0x2000

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

    private fun formNetwork() = executeCommand(networkManager, arrayOf("netstart", "form", "${networkManager.zigBeePanId}", "${networkManager.zigBeeExtendedPanId}"))

    private fun savedDevices() = dataStore.readNetworkNodes()
            .map(dataStore::readNode)
            .mapNotNull(this::nodeToZigBeeDevice)
            .let { devices ->
                printWriter.println(Payload(this@ZigBeeProtocol.javaClass.name).apply {
                    action = SAVED_DEVICES
                    data = devices.serializeList()
                    response = getString(R.string.zigbeeprotocol_saved_devices_request)
                    appendCommands()
                }.serialize())
            }

    private fun nodeToZigBeeDevice(node: ZigBeeNodeDao): ZigBeeDevice? =
            node.endpoints.find { it.inputClusters.map { cluster -> cluster.clusterId }.contains(ZclClusterType.ON_OFF.id) }?.let { endpoint ->
                ZigBeeDevice(node.ieeeAddress.toString(), node.networkAddress.toString(), endpoint.endpointId.toString(), node.ieeeAddress.toString())
            }

    private fun Payload.appendCommands() {
        addCommand(RESET)
        addCommand(FORM_NETWORK)
        addCommand(SAVED_DEVICES)
        availableCommands.keys.forEach { addCommand(it) }
    }

    private fun post(vararg messages: String) {
        val out = messages.commandString()

        if (thread.isAlive) outputProcessor.onNext(Payload(this@ZigBeeProtocol.javaClass.name).apply {
            response = out
            appendCommands()
        }.serialize())

        Log.i("ZIGBEE", out)
    }

    private fun Array<out String>.commandString() = joinToString(separator = " ")

    override fun close() {
        disposable.clear()
        networkManager.shutdown()
        thread.quitSafely()
    }

    companion object {
        const val TI_VENDOR_ID = 1105
        const val CC2531_PRODUCT_ID = 5800
        const val BAUD_RATE = 115200
        const val MESH_UPDATE_PERIOD = 60
        const val OUTPUT_BUFFER_RATE = 100L

        const val FORM_NETWORK = "formnet"
    }
}