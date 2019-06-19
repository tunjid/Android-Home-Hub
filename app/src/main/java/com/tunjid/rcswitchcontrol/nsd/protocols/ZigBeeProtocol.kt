package com.tunjid.rcswitchcontrol.nsd.protocols

import android.content.Context.USB_SERVICE
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.HandlerThread
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.model.Payload
import com.zsmartsystems.zigbee.ExtendedPanId
import com.zsmartsystems.zigbee.ZigBeeChannel
import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import com.zsmartsystems.zigbee.ZigBeeStatus
import com.zsmartsystems.zigbee.console.ZigBeeConsoleCommand
import com.zsmartsystems.zigbee.dongle.cc2531.ZigBeeDongleTiCc2531
import com.zsmartsystems.zigbee.security.ZigBeeKey
import com.zsmartsystems.zigbee.serialization.DefaultDeserializer
import com.zsmartsystems.zigbee.serialization.DefaultSerializer
import com.zsmartsystems.zigbee.transport.TransportConfig
import com.zsmartsystems.zigbee.transport.TransportConfigOption
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster
import java.io.PrintWriter


class ZigBeeProtocol(printWriter: PrintWriter) : CommsProtocol(printWriter) {

    private val thread = HandlerThread("ZigBee").apply { start() }
    private val handler = Handler(thread.looper)

    init {
        val manager: UsbManager = App.instance.getSystemService(USB_SERVICE) as UsbManager

        val customTable = ProbeTable()
        customTable.addProduct(1105, 5800, CdcAcmSerialDriver::class.java)

        val drivers = UsbSerialProber(customTable).findAllDrivers(manager)

        if (drivers.isNotEmpty()) handler.post { start(drivers[0]) }
    }

    override fun processInput(payload: Payload): Payload {
        val builder = Payload.builder()
        builder.setKey(javaClass.name).addCommand(RESET)

        return builder.build()
    }

    private fun start(driver: UsbSerialDriver) {
        val resetNetwork = true
        val transportOptions = TransportConfig()
        val dongle = ZigBeeDongleTiCc2531(AndroidSerialPort(driver, 9600))

        val networkManager = ZigBeeNetworkManager(dongle).apply {
            //            setNetworkDataStore(ZigBeeDataStore(dongleName))
            setSerializer(DefaultSerializer::class.java, DefaultDeserializer::class.java)
        }

        val console = ZigBeeConsole(networkManager, dongle, emptyList<Class<out ZigBeeConsoleCommand>>())

        // Initialise the network
        val initResponse = networkManager.initialize()
        println("networkManager.initialize returned $initResponse")

        if (initResponse != ZigBeeStatus.SUCCESS) {
            console.start()
            println("Console closed.")
            return
        }

        System.out.println("PAN ID          = " + networkManager.zigBeePanId)
        System.out.println("Extended PAN ID = " + networkManager.zigBeeExtendedPanId)
        System.out.println("Channel         = " + networkManager.zigBeeChannel)

        if (resetNetwork) reset(networkManager)

        // Add the default ZigBeeAlliance09 HA link key

        transportOptions.addOption(TransportConfigOption.TRUST_CENTRE_LINK_KEY, ZigBeeKey(intArrayOf(0x5A, 0x69, 0x67, 0x42, 0x65, 0x65, 0x41, 0x6C, 0x6C, 0x69, 0x61, 0x6E, 0x63, 0x65, 0x30, 0x39)))
        // transportOptions.addOption(TransportConfigOption.TRUST_CENTRE_LINK_KEY, new ZigBeeKey(new int[] { 0x41, 0x61,
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

        console.start()

        println("Console closed.")
    }

    private fun reset(networkManager: ZigBeeNetworkManager) {
        val nwkKey: ZigBeeKey = ZigBeeKey.createRandom()
        val linkKey = ZigBeeKey(intArrayOf(0x5A, 0x69, 0x67, 0x42, 0x65, 0x65, 0x41, 0x6C, 0x6C, 0x69, 0x61, 0x6E, 0x63, 0x65, 0x30, 0x39))
        val extendedPan = ExtendedPanId()
        val channel = 11
        val pan = 1

        println("*** Resetting network")
        println("  * Channel                = $channel")
        println("  * PAN ID                 = $pan")
        println("  * Extended PAN ID        = $extendedPan")
        println("  * Link Key               = $linkKey")

        if (nwkKey.hasOutgoingFrameCounter()) {
            println("  * Link Key Frame Cnt     = " + linkKey.outgoingFrameCounter!!)
        }
        println("  * Network Key            = $nwkKey")

        if (nwkKey.hasOutgoingFrameCounter()) {
            println("  * Network Key Frame Cnt  = " + nwkKey.outgoingFrameCounter!!)
        }

        networkManager.zigBeeChannel = ZigBeeChannel.create(channel)
        networkManager.zigBeePanId = pan
        networkManager.zigBeeExtendedPanId = extendedPan
        networkManager.zigBeeNetworkKey = nwkKey
        networkManager.zigBeeLinkKey = linkKey
    }

    override fun close() {
        thread.quitSafely()
    }
}