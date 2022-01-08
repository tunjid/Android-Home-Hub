package com.rcswitchcontrol.zigbee.protocol

import com.rcswitchcontrol.protocols.CommonDeviceActions
import com.rcswitchcontrol.zigbee.R
import com.rcswitchcontrol.zigbee.persistence.ZigBeeDataStore
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol.Companion.MESH_UPDATE_PERIOD
import com.tunjid.rcswitchcontrol.common.ContextProvider
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
import com.zsmartsystems.zigbee.security.ZigBeeKey
import com.zsmartsystems.zigbee.serialization.DefaultDeserializer
import com.zsmartsystems.zigbee.serialization.DefaultSerializer
import com.zsmartsystems.zigbee.transport.DeviceType
import com.zsmartsystems.zigbee.transport.TransportConfig
import com.zsmartsystems.zigbee.transport.TransportConfigOption
import com.zsmartsystems.zigbee.transport.TrustCentreJoinMode
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge


internal suspend fun initialize(
    action: Action.Input.Start
): Action.Input.InitializationStatus {
    val inputs = MutableSharedFlow<Action.Input>(
        replay = 1,
        extraBufferCapacity = 3,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val outputs = MutableSharedFlow<Action.Output>(
        replay = 1,
        extraBufferCapacity = 3,
        onBufferOverflow = BufferOverflow.SUSPEND
    )
    val synchronousOutputs = mutableListOf<Action.Output>()
    val onOutput: (Action.Output) -> Unit = if (outputs.subscriptionCount.value > 0) {
        { outputs.tryEmit(it) }
    } else synchronousOutputs::add

    val (_, dongle, dataStoreName) = action
    val transportOptions = TransportConfig()
    val dongleBackend = dongle.createBackend(transportOptions)
    val networkManager = ZigBeeNetworkManager(dongleBackend)
    val dataStore = ZigBeeDataStore(dataStoreName)

    if (!dataStore.hasNoDevices) synchronousOutputs.add(Action.Output.PayloadOutput(payload = ZigBeeProtocol.zigBeePayload(
        action = CommonDeviceActions.refreshDevicesAction,
        response = "Requesting saved Devices",
        data = dataStore.savedDevices.serializeList()
    )))

    val resetNetwork = dataStore.hasNoDevices

    networkManager.apply {
        setNetworkDataStore(dataStore)
        setSerializer(DefaultSerializer::class.java, DefaultDeserializer::class.java)

        addNetworkStateListener { state ->
            onOutput(Action.Output.Log("ZigBee network state updated to $state"))
        }

        addNetworkNodeListener(object : ZigBeeNetworkNodeListener {
            override fun nodeAdded(node: ZigBeeNode) {
                inputs.tryEmit(Action.Input.NodeChange.Added(node))
                onOutput(Action.Output.Log("Node added $node"))
            }

            override fun nodeUpdated(node: ZigBeeNode) {
                outputs.tryEmit(Action.Output.Log("Node updated $node"))
            }

            override fun nodeRemoved(node: ZigBeeNode) {
                inputs.tryEmit(Action.Input.NodeChange.Removed(node))
                onOutput(Action.Output.Log("Node removed $node"))
            }
        })

        addCommandListener {}
    }
    // Initialise the network

    val initResponse = networkManager.initialize()

    if (initResponse != ZigBeeStatus.SUCCESS) return Action.Input.InitializationStatus.Error

    synchronousOutputs.add(Action.Output.Log("PAN ID          = " + networkManager.zigBeePanId))
    synchronousOutputs.add(Action.Output.Log("Extended PAN ID = " + networkManager.zigBeeExtendedPanId))
    synchronousOutputs.add(Action.Output.Log("Channel         = " + networkManager.zigBeeChannel))

    if (resetNetwork) synchronousOutputs.add(networkManager.reset())

//        networkManager.setDefaultProfileId(ZigBeeProfileType.ZIGBEE_HOME_AUTOMATION.key)

    transportOptions.apply {
        addOption(TransportConfigOption.DEVICE_TYPE, DeviceType.COORDINATOR)
        addOption(TransportConfigOption.TRUST_CENTRE_JOIN_MODE, TrustCentreJoinMode.TC_JOIN_SECURE)
        addOption(TransportConfigOption.TRUST_CENTRE_LINK_KEY, ZigBeeKey(intArrayOf(
            0x5A, 0x69, 0x67, 0x42, 0x65, 0x65, 0x41, 0x6C, 0x6C, 0x69, 0x61, 0x6E, 0x63, 0x65, 0x30, 0x39
        )))
    }

    dongleBackend.updateTransportConfig(transportOptions)

    networkManager.apply {
        addExtension(ZigBeeIasCieExtension())
        addExtension(ZigBeeOtaUpgradeExtension())
        addExtension(ZigBeeBasicServerExtension())
        addExtension(ZigBeeDiscoveryExtension().apply {
            updatePeriod = MESH_UPDATE_PERIOD
//            setUpdateOnChange(false)
        })
    }

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

    synchronousOutputs.add(Action.Output.Log(
        if (networkManager.startup(resetNetwork) !== ZigBeeStatus.SUCCESS) "ZigBee console starting up ... [FAIL]"
        else "ZigBee console starting up ... [OK]"
    ))

    dongleBackend.postSetup()

    return Action.Input.InitializationStatus.Initialized(
        startAction = action,
        dataStore = dataStore,
        networkManager = networkManager,
        inputs = inputs,
        outputs = flow {
            emitAll(
                merge(
                    synchronousOutputs.toList().asFlow(),
                    outputs
                )
            )
        }
    )
}

private fun ZigBeeNetworkManager.reset(): Action.Output.Log {
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


    zigBeeChannel = ZigBeeChannel.create(channel)
    zigBeePanId = pan
    zigBeeExtendedPanId = extendedPan
    zigBeeNetworkKey = nwkKey
    zigBeeLinkKey = linkKey

    return Action.Output.Log(stringBuilder.toString())
}

//private fun formNetwork() = actionProcessor.onNext(Action.Input.CommandInput(
//    NamedCommand.Custom.NetworkStart.consoleCommand,
//    listOf(ContextProvider.appContext.getString(R.string.zigbeeprotocol_netstart), "${networkManager.zigBeePanId}", "${networkManager.zigBeeExtendedPanId}")
//))