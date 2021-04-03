package com.rcswitchcontrol.zigbee.protocol

import android.hardware.usb.UsbDevice
import com.rcswitchcontrol.zigbee.io.AndroidZigBeeSerialPort
import com.tunjid.rcswitchcontrol.common.SerialInfo
import com.zsmartsystems.zigbee.dongle.cc2531.ZigBeeDongleTiCc2531
import com.zsmartsystems.zigbee.dongle.ember.ZigBeeDongleEzsp
import com.zsmartsystems.zigbee.dongle.ember.ezsp.structure.EzspConfigId
import com.zsmartsystems.zigbee.transport.ConcentratorConfig
import com.zsmartsystems.zigbee.transport.ConcentratorType
import com.zsmartsystems.zigbee.transport.TransportConfig
import com.zsmartsystems.zigbee.transport.TransportConfigOption
import com.zsmartsystems.zigbee.transport.ZigBeeTransportTransmit


sealed class Dongle {

    companion object {
        val cc2531SerialInfo = SerialInfo(
            vendorId = 0x0451,
            productId = 0x16a8,
            baudRate = 115200
        )
        val emberThunderBoard2SerialInfo = SerialInfo(
            vendorId = 0x1366,
            productId = 0x1015,
            baudRate = 57600
        )
    }

    abstract val usbDevice: UsbDevice

    data class CC2531(
        override val usbDevice: UsbDevice,
    ) : Dongle()

    data class SiLabsThunderBoard2(
        override val usbDevice: UsbDevice,
    ) : Dongle()

    val serialInfo: SerialInfo
        get() = when (this) {
            is CC2531 -> cc2531SerialInfo
            is SiLabsThunderBoard2 -> emberThunderBoard2SerialInfo
        }

    fun createBackend(transportOptions: TransportConfig): ZigBeeTransportTransmit = when (this) {
        is CC2531 -> ZigBeeDongleTiCc2531(AndroidZigBeeSerialPort(serialInfo, usbDevice)).apply {
            transportOptions.addOption(TransportConfigOption.RADIO_TX_POWER, 3)
        }
        is SiLabsThunderBoard2 -> ZigBeeDongleEzsp(AndroidZigBeeSerialPort(serialInfo, usbDevice)).apply {
            this.updateDefaultConfiguration(EzspConfigId.EZSP_CONFIG_SOURCE_ROUTE_TABLE_SIZE, 32)
            this.updateDefaultConfiguration(EzspConfigId.EZSP_CONFIG_APS_UNICAST_MESSAGE_COUNT, 16)
            this.updateDefaultConfiguration(EzspConfigId.EZSP_CONFIG_NEIGHBOR_TABLE_SIZE, 24)

            transportOptions.addOption(TransportConfigOption.RADIO_TX_POWER, 8)

            // Configure the concentrator
            // Max Hops defaults to system max

            // Configure the concentrator
            // Max Hops defaults to system max
            val concentratorConfig = ConcentratorConfig()
            concentratorConfig.type = ConcentratorType.HIGH_RAM
            concentratorConfig.maxFailures = 8
            concentratorConfig.maxHops = 0
            concentratorConfig.refreshMinimum = 60
            concentratorConfig.refreshMaximum = 3600
            transportOptions.addOption(TransportConfigOption.CONCENTRATOR_CONFIG, concentratorConfig)

            // Add transport specific console commands

            // Add transport specific console commands
        }
    }
}

fun ZigBeeTransportTransmit.postSetup() = when (this) {
    is ZigBeeDongleTiCc2531 -> {
        setLedMode(1, false)
        setLedMode(2, false)
        Unit
    }
    else -> throw IllegalArgumentException("Unsupported backend")
}