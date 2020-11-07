package com.rcswitchcontrol.zigbee.protocol

import com.rcswitchcontrol.zigbee.R
import com.rcswitchcontrol.zigbee.commands.*
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.zsmartsystems.zigbee.console.*

internal sealed class CommandParser {
    data class Custom(val consoleCommand: AbsZigBeeCommand) : CommandParser()
    sealed class Derived(private val stringRes: Int, val consoleCommand: ZigBeeConsoleCommand) : CommandParser() {
        val key: String get() = ContextProvider.appContext.getString(stringRes)

        object ZigBeeConsoleNodeListCommand: CommandParser.Derived(R.string.zigbeeprotocol_nodes, ZigBeeConsoleNodeListCommand())
        object ZigBeeConsoleDescribeEndpointCommand: CommandParser.Derived(R.string.zigbeeprotocol_endpoint, ZigBeeConsoleDescribeEndpointCommand())
        object ZigBeeConsoleDescribeNodeCommand: CommandParser.Derived(R.string.zigbeeprotocol_node, ZigBeeConsoleDescribeNodeCommand())
        object ZigBeeConsoleBindCommand: CommandParser.Derived(R.string.zigbeeprotocol_bind, ZigBeeConsoleBindCommand())
        object ZigBeeConsoleUnbindCommand: CommandParser.Derived(R.string.zigbeeprotocol_unbind, ZigBeeConsoleUnbindCommand())
        object ZigBeeConsoleBindingTableCommand: CommandParser.Derived(R.string.zigbeeprotocol_bind_table, ZigBeeConsoleBindingTableCommand())

        object ZigBeeConsoleAttributeReadCommand: CommandParser.Derived(R.string.zigbeeprotocol_read, ZigBeeConsoleAttributeReadCommand())
        object ZigBeeConsoleAttributeWriteCommand: CommandParser.Derived(R.string.zigbeeprotocol_write, ZigBeeConsoleAttributeWriteCommand())

        object ZigBeeConsoleAttributeSupportedCommand: CommandParser.Derived(R.string.zigbeeprotocol_attsupported, ZigBeeConsoleAttributeSupportedCommand())
        object ZigBeeConsoleCommandsSupportedCommand: CommandParser.Derived(R.string.zigbeeprotocol_cmdsupported, ZigBeeConsoleCommandsSupportedCommand())

        object ZigBeeConsoleDeviceInformationCommand: CommandParser.Derived(R.string.zigbeeprotocol_info, ZigBeeConsoleDeviceInformationCommand())
        object ZigBeeConsoleNetworkJoinCommand: CommandParser.Derived(R.string.zigbeeprotocol_join, ZigBeeConsoleNetworkJoinCommand())
        object ZigBeeConsoleNetworkLeaveCommand: CommandParser.Derived(R.string.zigbeeprotocol_leave, ZigBeeConsoleNetworkLeaveCommand())

        object ZigBeeConsoleReportingConfigCommand: CommandParser.Derived(R.string.zigbeeprotocol_reporting, ZigBeeConsoleReportingConfigCommand())
        object ZigBeeConsoleReportingSubscribeCommand: CommandParser.Derived(R.string.zigbeeprotocol_subscribe, ZigBeeConsoleReportingSubscribeCommand())
        object ZigBeeConsoleReportingUnsubscribeCommand: CommandParser.Derived(R.string.zigbeeprotocol_unsubscribe, ZigBeeConsoleReportingUnsubscribeCommand())

        object ZigBeeConsoleInstallKeyCommand: CommandParser.Derived(R.string.zigbeeprotocol_installkey, ZigBeeConsoleInstallKeyCommand())
        object ZigBeeConsoleLinkKeyCommand: CommandParser.Derived(R.string.zigbeeprotocol_linkkey, ZigBeeConsoleLinkKeyCommand())

        object ZigBeeConsoleNetworkStartCommand: CommandParser.Derived(R.string.zigbeeprotocol_netstart, ZigBeeConsoleNetworkStartCommand())
        object ZigBeeConsoleNetworkBackupCommand: CommandParser.Derived(R.string.zigbeeprotocol_netbackup, ZigBeeConsoleNetworkBackupCommand())
        object ZigBeeConsoleNetworkDiscoveryCommand: CommandParser.Derived(R.string.zigbeeprotocol_discovery, ZigBeeConsoleNetworkDiscoveryCommand())

        object ZigBeeConsoleOtaUpgradeCommand: CommandParser.Derived(R.string.zigbeeprotocol_otaupgrade, ZigBeeConsoleOtaUpgradeCommand())
        object ZigBeeConsoleChannelCommand: CommandParser.Derived(R.string.zigbeeprotocol_channel, ZigBeeConsoleChannelCommand())
    }
}

internal fun generateAvailableCommands(): Map<String, ZigBeeConsoleCommand> = mutableMapOf(
        CommandParser.Derived.ZigBeeConsoleNodeListCommand.keyedPair,
        CommandParser.Derived.ZigBeeConsoleDescribeEndpointCommand.keyedPair,
        CommandParser.Derived.ZigBeeConsoleDescribeNodeCommand.keyedPair,
        CommandParser.Derived.ZigBeeConsoleBindCommand.keyedPair,
        CommandParser.Derived.ZigBeeConsoleUnbindCommand.keyedPair,
        CommandParser.Derived.ZigBeeConsoleBindingTableCommand.keyedPair,

        CommandParser.Derived.ZigBeeConsoleAttributeReadCommand.keyedPair,
        CommandParser.Derived.ZigBeeConsoleAttributeWriteCommand.keyedPair,

        CommandParser.Derived.ZigBeeConsoleAttributeSupportedCommand.keyedPair,
        CommandParser.Derived.ZigBeeConsoleCommandsSupportedCommand.keyedPair,

        CommandParser.Derived.ZigBeeConsoleDeviceInformationCommand.keyedPair,
        CommandParser.Derived.ZigBeeConsoleNetworkJoinCommand.keyedPair,
        CommandParser.Derived.ZigBeeConsoleNetworkLeaveCommand.keyedPair,

        CommandParser.Derived.ZigBeeConsoleReportingConfigCommand.keyedPair,
        CommandParser.Derived.ZigBeeConsoleReportingSubscribeCommand.keyedPair,
        CommandParser.Derived.ZigBeeConsoleReportingUnsubscribeCommand.keyedPair,

        CommandParser.Derived.ZigBeeConsoleInstallKeyCommand.keyedPair,
        CommandParser.Derived.ZigBeeConsoleLinkKeyCommand.keyedPair,

        CommandParser.Derived.ZigBeeConsoleNetworkStartCommand.keyedPair,
        CommandParser.Derived.ZigBeeConsoleNetworkBackupCommand.keyedPair,
        CommandParser.Derived.ZigBeeConsoleNetworkDiscoveryCommand.keyedPair,

        CommandParser.Derived.ZigBeeConsoleOtaUpgradeCommand.keyedPair,
        CommandParser.Derived.ZigBeeConsoleChannelCommand.keyedPair,

        CommandParser.Custom(OnCommand()).keyedPair,
        CommandParser.Custom(OffCommand()).keyedPair,
        CommandParser.Custom(ColorCommand()).keyedPair,
        CommandParser.Custom(LevelCommand()).keyedPair,

        CommandParser.Custom(GroupAddCommand()).keyedPair,
        CommandParser.Custom(GroupRemoveCommand()).keyedPair,
        CommandParser.Custom(GroupListCommand()).keyedPair,

        CommandParser.Custom(MembershipAddCommand()).keyedPair,
        CommandParser.Custom(MembershipRemoveCommand()).keyedPair,
        CommandParser.Custom(MembershipViewCommand()).keyedPair,
        CommandParser.Custom(MembershipListCommand()).keyedPair,

        CommandParser.Custom(RediscoverCommand()).keyedPair

).let { it + CommandParser.Custom(HelpCommand(it)).keyedPair }


private val CommandParser.keyedPair
    get() = when (this) {
        is CommandParser.Derived -> key to consoleCommand
        is CommandParser.Custom -> consoleCommand.command to consoleCommand
    }