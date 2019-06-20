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
import com.tunjid.rcswitchcontrol.model.Payload
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
import java.io.PrintWriter
import java.util.*


class ZigBeeProtocol(printWriter: PrintWriter) : CommsProtocol(printWriter) {

    private val thread = HandlerThread("ZigBee").apply { start() }
    private val handler = Handler(thread.looper)

    private val dongle: ZigBeeDongleTiCc2531
    private val networkManager: ZigBeeNetworkManager
    private val availableCommands: MutableMap<String, ZigBeeConsoleCommand> = TreeMap()

    init {
        val manager: UsbManager = App.instance.getSystemService(USB_SERVICE) as UsbManager

        val customTable = ProbeTable()
        customTable.addProduct(1105, 5800, CdcAcmSerialDriver::class.java)

        val drivers = UsbSerialProber(customTable).findAllDrivers(manager)

        if (drivers.isEmpty()) throw IllegalArgumentException("No driver available")

        val driver = drivers[0]

        dongle = ZigBeeDongleTiCc2531(AndroidSerialPort(driver, 9600))
        networkManager = ZigBeeNetworkManager(dongle).apply {
            //            setNetworkDataStore(ZigBeeDataStore(dongleName))
            addExtension(ZigBeeIasCieExtension())
            addExtension(ZigBeeOtaUpgradeExtension())
            addExtension(ZigBeeBasicServerExtension())
            addExtension(ZigBeeDiscoveryExtension().apply { updatePeriod = 60 })

            setSerializer(DefaultSerializer::class.java, DefaultDeserializer::class.java)

            addNetworkStateListener { state -> print("ZigBee network state updated to $state") }

            addNetworkNodeListener(object : ZigBeeNetworkNodeListener {
                override fun nodeAdded(node: ZigBeeNode) = print("Node Added $node")

                override fun nodeUpdated(node: ZigBeeNode) = print("Node Updated $node")

                override fun nodeRemoved(node: ZigBeeNode) = print("Node Removed $node")
            })

            addCommandListener {}
        }

        availableCommands["nodes"] = ZigBeeConsoleNodeListCommand()
        availableCommands["endpoint"] = ZigBeeConsoleDescribeEndpointCommand()
        availableCommands["node"] = ZigBeeConsoleDescribeNodeCommand()
        availableCommands["bind"] = ZigBeeConsoleBindCommand()
        availableCommands["unbind"] = ZigBeeConsoleUnbindCommand()
        availableCommands["bindtable"] = ZigBeeConsoleBindingTableCommand()

        availableCommands["read"] = ZigBeeConsoleAttributeReadCommand()
        availableCommands["write"] = ZigBeeConsoleAttributeWriteCommand()

        availableCommands["attsupported"] = ZigBeeConsoleAttributeSupportedCommand()
        availableCommands["cmdsupported"] = ZigBeeConsoleCommandsSupportedCommand()

        availableCommands["info"] = ZigBeeConsoleDeviceInformationCommand()
        availableCommands["join"] = ZigBeeConsoleNetworkJoinCommand()
        availableCommands["leave"] = ZigBeeConsoleNetworkLeaveCommand()

        availableCommands["reporting"] = ZigBeeConsoleReportingConfigCommand()
        availableCommands["subscribe"] = ZigBeeConsoleReportingSubscribeCommand()
        availableCommands["unsubscribe"] = ZigBeeConsoleReportingUnsubscribeCommand()

        availableCommands["installkey"] = ZigBeeConsoleInstallKeyCommand()
        availableCommands["linkkey"] = ZigBeeConsoleLinkKeyCommand()

        availableCommands["netstart"] = ZigBeeConsoleNetworkStartCommand()
        availableCommands["netbackup"] = ZigBeeConsoleNetworkBackupCommand()
        availableCommands["discovery"] = ZigBeeConsoleNetworkDiscoveryCommand()

        availableCommands["otaupgrade"] = ZigBeeConsoleOtaUpgradeCommand()
        availableCommands["channel"] = ZigBeeConsoleChannelCommand()

        handler.post { start() }
    }

    override fun processInput(payload: Payload): Payload {
        val builder = Payload.builder()
        builder.setKey(javaClass.name).addCommand(RESET)

        when (payload.action) {
            PING -> builder.setResponse(getString(R.string.zigbeeprotocol_ping))
            else -> builder.setResponse("Ummm")
        }

        availableCommands.keys.forEach { builder.addCommand(it) }

        return builder.build()
    }

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

        if (resetNetwork) reset(networkManager)

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

    private fun reset(networkManager: ZigBeeNetworkManager) {
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

        print(stringBuilder.toString())
    }

    private fun print(message: String) {
        val builder = Payload.builder().apply {
            setKey(this@ZigBeeProtocol.javaClass.name)
            setResponse(message)
        }

        handler.post { printWriter.println(builder.build().serialize()) }
    }

    override fun close() {
        thread.quitSafely()
        networkManager.shutdown()
    }
}