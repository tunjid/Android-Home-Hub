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

package com.rcswitchcontrol.zigbee.protocol

import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.io.ConsoleStream
import com.rcswitchcontrol.protocols.models.Payload
import com.rcswitchcontrol.zigbee.R
import com.rcswitchcontrol.zigbee.io.AndroidZigBeeSerialPort
import com.rcswitchcontrol.zigbee.models.ZigBeeCommand
import com.rcswitchcontrol.zigbee.models.ZigBeeCommandInfo
import com.rcswitchcontrol.zigbee.models.ZigBeeDevice
import com.rcswitchcontrol.zigbee.persistence.ZigBeeDataStore
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.tunjid.rcswitchcontrol.common.deserialize
import com.tunjid.rcswitchcontrol.common.serialize
import com.tunjid.rcswitchcontrol.common.serializeList
import com.zsmartsystems.zigbee.ExtendedPanId
import com.zsmartsystems.zigbee.ZigBeeChannel
import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import com.zsmartsystems.zigbee.ZigBeeNetworkNodeListener
import com.zsmartsystems.zigbee.ZigBeeNetworkState
import com.zsmartsystems.zigbee.ZigBeeNode
import com.zsmartsystems.zigbee.ZigBeeStatus
import com.zsmartsystems.zigbee.app.basic.ZigBeeBasicServerExtension
import com.zsmartsystems.zigbee.app.discovery.ZigBeeDiscoveryExtension
import com.zsmartsystems.zigbee.app.iasclient.ZigBeeIasCieExtension
import com.zsmartsystems.zigbee.app.otaserver.ZigBeeOtaUpgradeExtension
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

@Suppress("PrivatePropertyName")
class ZigBeeProtocol(driver: UsbSerialDriver, printWriter: PrintWriter) : CommsProtocol(printWriter) {

    private val SAVED_DEVICES = ContextProvider.appContext.getString(R.string.zigbeeprotocol_saved_devices)
    private val FORM_NETWORK = ContextProvider.appContext.getString(R.string.zigbeeprotocol_formnet)

    private val disposable = CompositeDisposable()
    private val outputProcessor: PublishProcessor<Payload> = PublishProcessor.create()

    private val outStream = ConsoleStream { post(it) }

    private val dongle: ZigBeeDongleTiCc2531
    private val dataStore = ZigBeeDataStore("home")
    private val networkManager: ZigBeeNetworkManager
    private val availableCommands: Map<String, NamedCommand> = generateAvailableCommands()

    init {
        dongle = ZigBeeDongleTiCc2531(AndroidZigBeeSerialPort(driver, BAUD_RATE))
        networkManager = ZigBeeNetworkManager(dongle).apply {
            setNetworkDataStore(dataStore)
            addExtension(ZigBeeIasCieExtension())
            addExtension(ZigBeeOtaUpgradeExtension())
            addExtension(ZigBeeBasicServerExtension())
            addExtension(ZigBeeDiscoveryExtension().apply { updatePeriod = MESH_UPDATE_PERIOD })

            setSerializer(DefaultSerializer::class.java, DefaultDeserializer::class.java)

            addNetworkStateListener { state ->
                post("ZigBee network state updated to $state")
                if (dataStore.hasNoDevices && ZigBeeNetworkState.ONLINE == state) formNetwork()
            }

            addNetworkNodeListener(object : ZigBeeNetworkNodeListener {
                override fun nodeAdded(node: ZigBeeNode) = post("Node Added $node")

                override fun nodeUpdated(node: ZigBeeNode) = post("Node Updated $node")

                override fun nodeRemoved(node: ZigBeeNode) = post("Node Removed $node")
            })

            addCommandListener {}
        }

        processOutput()
        sharedPool.submit(this::start)
    }

    override fun processInput(payload: Payload): Payload = Payload(javaClass.name).apply {
        addCommand(RESET)

        when (val action = payload.action ?: "invalid command") {
            RESET -> reset()
            FORM_NETWORK -> {
                response = ContextProvider.appContext.getString(R.string.zigbeeprotocol_forming_network)
                formNetwork()
            }
            PING, SAVED_DEVICES -> {
                response = (ContextProvider.appContext.getString(R.string.zigbeeprotocol_ping))
                this.action = SAVED_DEVICES
                data = savedDevices()
                response = ContextProvider.appContext.getString(R.string.zigbeeprotocol_saved_devices_request)
                appendCommands()
            }
            in availableCommands.keys -> {
                val mapper = availableCommands.getValue(action)
                val consoleCommand = mapper.consoleCommand
                val command = payload.data?.deserialize(ZigBeeCommand::class)
                val needsCommandArgs: Boolean = (command == null || command.isInvalid) && consoleCommand.syntax.isNotEmpty()

                when {
                    needsCommandArgs -> {
                        response = ContextProvider.appContext.getString(R.string.zigbeeprotocol_enter_args, action)
                        data = ZigBeeCommandInfo(action, consoleCommand.description, consoleCommand.syntax, consoleCommand.help).serialize()
                    }
                    else -> {
                        val args = command?.args?.toTypedArray() ?: arrayOf(action)
                        response = ContextProvider.appContext.getString(R.string.zigbeeprotocol_executing, args.commandString())

                        try {
                            mapper.executeCommand(args)
                        } catch (e: Exception) {
                            post("Exception in executing command: ${args.commandString()}")
                            e.printStackTrace()
                        }
                    }
                }
            }
            else -> response = "Unrecognized command"
        }

        appendCommands()
    }

    /**
     * Buffers writes to the output stream because quick concurrent writes cause data loss to the client
     */
    private fun processOutput() {
        disposable.add(outputProcessor
                .onBackpressureBuffer()
                .concatMap { Flowable.just(it).delay(OUTPUT_BUFFER_RATE, TimeUnit.MILLISECONDS) }
                .subscribe({ sharedPool.submit { pushOut(it) } }, { it.printStackTrace(); processOutput() }))
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

    /**
     * Executes command.
     *
     * @param args the arguments including the command
     */
    private fun NamedCommand.executeCommand(args: Array<String>) {
        sharedPool.submit {
            try {
                consoleCommand.process(networkManager, args, outStream)
            } catch (e: IllegalArgumentException) {
                post(
                        "Error executing command: ${e.message}",
                        "${consoleCommand.command} ${consoleCommand.syntax}"
                )
            } catch (e: IllegalStateException) {
                post(
                        "Error executing command: " + e.message,
                        consoleCommand.command + " " + consoleCommand.syntax
                )
            } catch (e: Exception) {
                post("Error executing command: $e")
            }
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

    private fun formNetwork() = NamedCommand.Derived.ZigBeeConsoleNetworkStartCommand.executeCommand(
            arrayOf(ContextProvider.appContext.getString(R.string.zigbeeprotocol_netstart), "form", "${networkManager.zigBeePanId}", "${networkManager.zigBeeExtendedPanId}")
    )

    private fun savedDevices() = dataStore.readNetworkNodes()
            .map(dataStore::readNode)
            .mapNotNull(this::nodeToZigBeeDevice)
            .serializeList()

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

        sharedPool.submit {
            outputProcessor.onNext(Payload(this@ZigBeeProtocol.javaClass.name).apply {
                response = out
                appendCommands()
            })
        }

        Log.i("IOT", out)
    }

    private fun Array<out String>.commandString() = joinToString(separator = " ")

    override fun close() {
        disposable.clear()
        networkManager.shutdown()
    }

    companion object {
        const val TI_VENDOR_ID = 0x0451
        const val CC2531_PRODUCT_ID = 0x16a8
        const val BAUD_RATE = 115200
        const val MESH_UPDATE_PERIOD = 60
        const val OUTPUT_BUFFER_RATE = 100L
    }
}