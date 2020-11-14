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

import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.io.ConsoleStream
import com.rcswitchcontrol.protocols.models.Payload
import com.rcswitchcontrol.zigbee.R
import com.rcswitchcontrol.zigbee.commands.PayloadPublishing
import com.rcswitchcontrol.zigbee.io.AndroidZigBeeSerialPort
import com.rcswitchcontrol.zigbee.models.ZigBeeCommand
import com.rcswitchcontrol.zigbee.models.ZigBeeCommandInfo
import com.rcswitchcontrol.zigbee.models.device
import com.rcswitchcontrol.zigbee.persistence.ZigBeeDataStore
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.tunjid.rcswitchcontrol.common.deserialize
import com.tunjid.rcswitchcontrol.common.serialize
import com.tunjid.rcswitchcontrol.common.serializeList
import com.zsmartsystems.zigbee.ExtendedPanId
import com.zsmartsystems.zigbee.ZigBeeChannel
import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import com.zsmartsystems.zigbee.ZigBeeNetworkNodeListener
import com.zsmartsystems.zigbee.ZigBeeNode
import com.zsmartsystems.zigbee.ZigBeeStatus
import com.zsmartsystems.zigbee.app.basic.ZigBeeBasicServerExtension
import com.zsmartsystems.zigbee.app.discovery.ZigBeeDiscoveryExtension
import com.zsmartsystems.zigbee.app.iasclient.ZigBeeIasCieExtension
import com.zsmartsystems.zigbee.app.otaserver.ZigBeeOtaUpgradeExtension
import com.zsmartsystems.zigbee.console.ZigBeeConsoleCommand
import com.zsmartsystems.zigbee.database.ZigBeeNodeDao
import com.zsmartsystems.zigbee.dongle.cc2531.ZigBeeDongleTiCc2531
import com.zsmartsystems.zigbee.security.ZigBeeKey
import com.zsmartsystems.zigbee.serialization.DefaultDeserializer
import com.zsmartsystems.zigbee.serialization.DefaultSerializer
import com.zsmartsystems.zigbee.transport.DeviceType
import com.zsmartsystems.zigbee.transport.TransportConfig
import com.zsmartsystems.zigbee.transport.TransportConfigOption
import com.zsmartsystems.zigbee.transport.TrustCentreJoinMode
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.processors.PublishProcessor
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import java.io.PrintWriter
import java.util.concurrent.TimeUnit

@Suppress("PrivatePropertyName")
class ZigBeeProtocol(driver: UsbSerialDriver, printWriter: PrintWriter) : CommsProtocol(printWriter) {

    private val disposable = CompositeDisposable()
    private val argsProcessor: PublishProcessor<Pair<ZigBeeConsoleCommand, Array<String>>> = PublishProcessor.create()
    private val outputProcessor: PublishProcessor<Payload> = PublishProcessor.create()

    private val responseStream = ConsoleStream { post(it) }
    private val payloadStream = ConsoleStream {
        it.takeIf(String::isNotBlank)
                ?.deserialize(Payload::class)
                ?.let(outputProcessor::onNext)
    }

    private val dongle: ZigBeeDongleTiCc2531 = ZigBeeDongleTiCc2531(AndroidZigBeeSerialPort(driver, BAUD_RATE))
    private val dataStore = ZigBeeDataStore("46")
    private val networkManager: ZigBeeNetworkManager = ZigBeeNetworkManager(dongle)

    init {
        val sharedScheduler = Schedulers.from(sharedPool)
        outputProcessor
                .onBackpressureBuffer()
                .concatMap {
                    Flowable.just(it)
                            .delay(OUTPUT_BUFFER_RATE, TimeUnit.MILLISECONDS, sharedScheduler)
                }
                .observeOn(sharedScheduler)
                .subscribe(this::pushOut)
                .addTo(disposable)

        argsProcessor.concatMapCompletable { (consoleCommand, args) ->
            Completable.fromCallable {
                consoleCommand.process(
                        networkManager,
                        args,
                        if (consoleCommand is PayloadPublishing) payloadStream else responseStream
                )
            }
                    .onErrorResumeNext {
                        post(
                                "Error executing command: ${it.message}",
                                "${consoleCommand.command} ${consoleCommand.syntax}"
                        )
                        Completable.complete()
                    }
                    .subscribeOn(sharedScheduler)
        }
                .subscribe()
                .addTo(disposable)
        sharedPool.submit(::start)
    }

    override fun processInput(payload: Payload): Payload = Payload(javaClass.name).apply {
        addCommand(RESET)

        when (val payloadAction = payload.action ?: "invalid command") {
            RESET -> reset()
            FORM_NETWORK -> {
                response = ContextProvider.appContext.getString(R.string.zigbeeprotocol_forming_network)
                formNetwork()
            }
            PING, SAVED_DEVICES -> {
                action = SAVED_DEVICES
                response = ContextProvider.appContext.getString(
                        if (payloadAction == PING) R.string.zigbeeprotocol_saved_devices_request
                        else R.string.zigbeeprotocol_ping
                )
                data = dataStore.readNetworkNodes()
                        .map(dataStore::readNode)
                        .mapNotNull(ZigBeeNodeDao::device)
                        .serializeList()
            }
            in availableCommands.keys -> {
                val mapper = availableCommands.getValue(payloadAction)
                val consoleCommand = mapper.consoleCommand
                val command = payload.data?.deserialize(ZigBeeCommand::class)
                val needsCommandArgs: Boolean = (command == null || command.isInvalid) && consoleCommand.syntax.isNotEmpty()

                when {
                    needsCommandArgs -> {
                        response = ContextProvider.appContext.getString(R.string.zigbeeprotocol_enter_args, payloadAction)
                        data = ZigBeeCommandInfo(payloadAction, consoleCommand.description, consoleCommand.syntax, consoleCommand.help).serialize()
                    }
                    else -> {
                        val args = command?.args?.toTypedArray() ?: arrayOf(payloadAction)
                        response = ContextProvider.appContext.getString(R.string.zigbeeprotocol_executing, args.commandString())
                        argsProcessor.onNext(mapper.consoleCommand to args)
                    }
                }
            }
            else -> response = "Unrecognized command $payloadAction"
        }

        appendZigBeeCommands()
    }

    private fun start() {
        val resetNetwork = dataStore.hasNoDevices
        val transportOptions = TransportConfig()

        networkManager.apply {
            setNetworkDataStore(dataStore)
            setSerializer(DefaultSerializer::class.java, DefaultDeserializer::class.java)

            addNetworkStateListener { state ->
                post("ZigBee network state updated to $state")
//                if (dataStore.hasNoDevices && ZigBeeNetworkState.ONLINE == state) formNetwork()
            }

            addNetworkNodeListener(object : ZigBeeNetworkNodeListener {
                override fun nodeAdded(node: ZigBeeNode) = post("Node Added $node")

                override fun nodeUpdated(node: ZigBeeNode) = post("Node Updated $node")

                override fun nodeRemoved(node: ZigBeeNode) = post("Node Removed $node")
            })

            addCommandListener {}
        }
        // Initialise the network
        val initResponse = networkManager.initialize()

        if (initResponse != ZigBeeStatus.SUCCESS) return close()

        post("PAN ID          = " + networkManager.zigBeePanId)
        post("Extended PAN ID = " + networkManager.zigBeeExtendedPanId)
        post("Channel         = " + networkManager.zigBeeChannel)

        networkManager.apply {
            addExtension(ZigBeeIasCieExtension())
            addExtension(ZigBeeOtaUpgradeExtension())
            addExtension(ZigBeeBasicServerExtension())
            addExtension(ZigBeeDiscoveryExtension())
            addExtension(LazyDiscoveryExtension())
        }

        if (resetNetwork) reset()

//        networkManager.setDefaultProfileId(ZigBeeProfileType.ZIGBEE_HOME_AUTOMATION.key)

        transportOptions.apply {
            addOption(TransportConfigOption.RADIO_TX_POWER, 3)
            addOption(TransportConfigOption.DEVICE_TYPE, DeviceType.COORDINATOR)
            addOption(TransportConfigOption.TRUST_CENTRE_JOIN_MODE, TrustCentreJoinMode.TC_JOIN_SECURE)
            addOption(TransportConfigOption.TRUST_CENTRE_LINK_KEY, ZigBeeKey(intArrayOf(0x5A, 0x69, 0x67, 0x42, 0x65, 0x65, 0x41, 0x6C, 0x6C, 0x69, 0x61, 0x6E, 0x63, 0x65, 0x30, 0x39)))
        }

        dongle.updateTransportConfig(transportOptions)

        networkManager.addSupportedCluster(ZclIasZoneCluster.CLUSTER_ID)

//        listOf(
//                ZclIasZoneCluster.CLUSTER_ID,
//                ZclBasicCluster.CLUSTER_ID,
//                ZclIdentifyCluster.CLUSTER_ID,
//                ZclGroupsCluster.CLUSTER_ID,
//                ZclScenesCluster.CLUSTER_ID,
//                ZclPollControlCluster.CLUSTER_ID,
//                ZclOnOffCluster.CLUSTER_ID,
//                ZclLevelControlCluster.CLUSTER_ID,
//                ZclColorControlCluster.CLUSTER_ID,
//                ZclPressureMeasurementCluster.CLUSTER_ID,
//                ZclThermostatCluster.CLUSTER_ID,
//                ZclWindowCoveringCluster.CLUSTER_ID,
//                1000
//        ).sorted().forEach(networkManager::addSupportedClientCluster)
//
//        listOf(
//                ZclBasicCluster.CLUSTER_ID,
//                ZclIdentifyCluster.CLUSTER_ID,
//                ZclGroupsCluster.CLUSTER_ID,
//                ZclScenesCluster.CLUSTER_ID,
//                ZclPollControlCluster.CLUSTER_ID,
//                ZclOnOffCluster.CLUSTER_ID,
//                ZclLevelControlCluster.CLUSTER_ID,
//                ZclColorControlCluster.CLUSTER_ID,
//                ZclPressureMeasurementCluster.CLUSTER_ID,
//                ZclWindowCoveringCluster.CLUSTER_ID,
//                1000
//        ).sorted().forEach(networkManager::addSupportedServerCluster)

        post(
                if (networkManager.startup(resetNetwork) !== ZigBeeStatus.SUCCESS) "ZigBee console starting up ... [FAIL]"
                else "ZigBee console starting up ... [OK]"
        )

        dongle.setLedMode(1, false)
        dongle.setLedMode(2, false)
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

    private fun formNetwork() = argsProcessor.onNext(Pair(
            NamedCommand.Custom.NetworkStart.consoleCommand,
            arrayOf(ContextProvider.appContext.getString(R.string.zigbeeprotocol_netstart), "${networkManager.zigBeePanId}", "${networkManager.zigBeeExtendedPanId}")
    ))

    private fun post(vararg messages: String) =
            outputProcessor.onNext(Payload(this@ZigBeeProtocol.javaClass.name).apply {
                response = messages.commandString()
                appendZigBeeCommands()
            })

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

        internal val SAVED_DEVICES get() = ContextProvider.appContext.getString(R.string.zigbeeprotocol_saved_devices)
        internal val FORM_NETWORK get() = ContextProvider.appContext.getString(R.string.zigbeeprotocol_formnet)
    }
}