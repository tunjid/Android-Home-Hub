package com.rcswitchcontrol.zigbee.protocol

import com.rcswitchcontrol.zigbee.io.AndroidZigBeeSerialPort
import com.tunjid.rcswitchcontrol.common.SerialInfo
import com.zsmartsystems.zigbee.dongle.ember.ZigBeeDongleEzsp
import com.zsmartsystems.zigbee.dongle.ember.ezsp.structure.EzspConfigId
import com.zsmartsystems.zigbee.transport.ConcentratorConfig
import com.zsmartsystems.zigbee.transport.ConcentratorType
import com.zsmartsystems.zigbee.transport.TransportConfigOption


sealed class Dongle {

    abstract val serialInfo: SerialInfo

    data class CC2531(
        override val serialInfo: SerialInfo = SerialInfo(
            vendorId = 0x0451,
            productId = 0x16a8,
            baudRate = 115200
        )
    ) : Dongle()

    data class SiLabsThunderBoard2(
        override val serialInfo: SerialInfo = SerialInfo(
            vendorId = 0x1366,
            productId = 0x1015,
            baudRate = 57600
        )
    ) : Dongle()

//    fun preSetup(serialPort: AndroidZigBeeSerialPort) = when (this) {
//        is CC2531 -> {
//            transportOptions.addOption(TransportConfigOption.RADIO_TX_POWER, 3)
//        }
//        is SiLabsThunderBoard2 -> {
//            val emberDongle = ZigBeeDongleEzsp(serialPort)
//            dongle = emberDongle
//
//            emberDongle.updateDefaultConfiguration(EzspConfigId.EZSP_CONFIG_SOURCE_ROUTE_TABLE_SIZE, 32)
//            emberDongle.updateDefaultConfiguration(EzspConfigId.EZSP_CONFIG_APS_UNICAST_MESSAGE_COUNT, 16)
//            emberDongle.updateDefaultConfiguration(EzspConfigId.EZSP_CONFIG_NEIGHBOR_TABLE_SIZE, 24)
//
//            transportOptions.addOption(TransportConfigOption.RADIO_TX_POWER, 8)
//
//            // Configure the concentrator
//            // Max Hops defaults to system max
//
//            // Configure the concentrator
//            // Max Hops defaults to system max
//            val concentratorConfig = ConcentratorConfig()
//            concentratorConfig.type = ConcentratorType.HIGH_RAM
//            concentratorConfig.maxFailures = 8
//            concentratorConfig.maxHops = 0
//            concentratorConfig.refreshMinimum = 60
//            concentratorConfig.refreshMaximum = 3600
//            transportOptions.addOption(TransportConfigOption.CONCENTRATOR_CONFIG, concentratorConfig)
//
//            // Add transport specific console commands
//
//            // Add transport specific console commands
//
//        }
//    }
//
//    fun postSetup() = when (this) {
//        is CC2531 -> TODO()
//        is SiLabsThunderBoard2 -> TODO()
//    }
}