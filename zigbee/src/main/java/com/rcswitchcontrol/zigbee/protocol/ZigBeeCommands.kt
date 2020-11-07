package com.rcswitchcontrol.zigbee.protocol

import com.rcswitchcontrol.zigbee.R
import com.rcswitchcontrol.zigbee.commands.*
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.zsmartsystems.zigbee.console.*


internal fun generateAvailableCommands(): Map<String, ZigBeeConsoleCommand> = mutableMapOf(
        ContextProvider.appContext.getString(R.string.zigbeeprotocol_nodes) to ZigBeeConsoleNodeListCommand(),
        ContextProvider.appContext.getString(R.string.zigbeeprotocol_endpoint) to ZigBeeConsoleDescribeEndpointCommand(),
        ContextProvider.appContext.getString(R.string.zigbeeprotocol_node) to ZigBeeConsoleDescribeNodeCommand(),
        ContextProvider.appContext.getString(R.string.zigbeeprotocol_bind) to ZigBeeConsoleBindCommand(),
        ContextProvider.appContext.getString(R.string.zigbeeprotocol_unbind) to ZigBeeConsoleUnbindCommand(),
        ContextProvider.appContext.getString(R.string.zigbeeprotocol_bind_table) to ZigBeeConsoleBindingTableCommand(),

        ContextProvider.appContext.getString(R.string.zigbeeprotocol_read) to ZigBeeConsoleAttributeReadCommand(),
        ContextProvider.appContext.getString(R.string.zigbeeprotocol_write) to ZigBeeConsoleAttributeWriteCommand(),

        ContextProvider.appContext.getString(R.string.zigbeeprotocol_attsupported) to ZigBeeConsoleAttributeSupportedCommand(),
        ContextProvider.appContext.getString(R.string.zigbeeprotocol_cmdsupported) to ZigBeeConsoleCommandsSupportedCommand(),

        ContextProvider.appContext.getString(R.string.zigbeeprotocol_info) to ZigBeeConsoleDeviceInformationCommand(),
        ContextProvider.appContext.getString(R.string.zigbeeprotocol_join) to ZigBeeConsoleNetworkJoinCommand(),
        ContextProvider.appContext.getString(R.string.zigbeeprotocol_leave) to ZigBeeConsoleNetworkLeaveCommand(),

        ContextProvider.appContext.getString(R.string.zigbeeprotocol_reporting) to ZigBeeConsoleReportingConfigCommand(),
        ContextProvider.appContext.getString(R.string.zigbeeprotocol_subscribe) to ZigBeeConsoleReportingSubscribeCommand(),
        ContextProvider.appContext.getString(R.string.zigbeeprotocol_unsubscribe) to ZigBeeConsoleReportingUnsubscribeCommand(),

        ContextProvider.appContext.getString(R.string.zigbeeprotocol_installkey) to ZigBeeConsoleInstallKeyCommand(),
        ContextProvider.appContext.getString(R.string.zigbeeprotocol_linkkey) to ZigBeeConsoleLinkKeyCommand(),

        ContextProvider.appContext.getString(R.string.zigbeeprotocol_netstart) to ZigBeeConsoleNetworkStartCommand(),
        ContextProvider.appContext.getString(R.string.zigbeeprotocol_netbackup) to ZigBeeConsoleNetworkBackupCommand(),
        ContextProvider.appContext.getString(R.string.zigbeeprotocol_discovery) to ZigBeeConsoleNetworkDiscoveryCommand(),

        ContextProvider.appContext.getString(R.string.zigbeeprotocol_otaupgrade) to ZigBeeConsoleOtaUpgradeCommand(),
        ContextProvider.appContext.getString(R.string.zigbeeprotocol_channel) to ZigBeeConsoleChannelCommand(),

        // These commands are created locally and have localized command names

        OnCommand().m,
        OffCommand().m,
        ColorCommand().m,
        LevelCommand().m,

        GroupAddCommand().m,
        GroupRemoveCommand().m,
        GroupListCommand().m,

        MembershipAddCommand().m,
        MembershipRemoveCommand().m,
        MembershipViewCommand().m,
        MembershipListCommand().m,

        RediscoverCommand().m

).let { it["help"] = HelpCommand(it); it.toMap() }


private val AbsZigBeeCommand.m get() = Pair(command, this)