package com.rcswitchcontrol.zigbee.protocol

import com.zsmartsystems.zigbee.ZigBeeStatus
import com.zsmartsystems.zigbee.app.ZigBeeNetworkExtension
import com.zsmartsystems.zigbee.app.discovery.ZigBeeDiscoveryExtension

class LazyDiscoveryExtension(
        private val backing: ZigBeeDiscoveryExtension = ZigBeeDiscoveryExtension()
) : ZigBeeNetworkExtension by backing {

    override fun extensionStartup(): ZigBeeStatus {
        val backed = backing.extensionStartup()

        backing.setUpdateOnChange(true)
        backing.updatePeriod = ZigBeeProtocol.MESH_UPDATE_PERIOD
        backing.refresh()

        return backed
    }
}