package com.rcswitchcontrol.zigbee.protocol

import com.rcswitchcontrol.zigbee.R
import com.rcswitchcontrol.zigbee.commands.*
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.zsmartsystems.zigbee.console.*

internal sealed class CommandMapper {
    data class Custom(val consoleCommand: AbsZigBeeCommand) : CommandMapper()
    sealed class Derived(private val stringRes: Int, val consoleCommand: ZigBeeConsoleCommand) : CommandMapper() {
        val key: String get() = ContextProvider.appContext.getString(stringRes)

        object ZigBeeConsoleNodeListCommand: CommandMapper.Derived(R.string.zigbeeprotocol_nodes, ZigBeeConsoleNodeListCommand())
        object ZigBeeConsoleDescribeEndpointCommand: CommandMapper.Derived(R.string.zigbeeprotocol_endpoint, ZigBeeConsoleDescribeEndpointCommand())
        object ZigBeeConsoleDescribeNodeCommand: CommandMapper.Derived(R.string.zigbeeprotocol_node, ZigBeeConsoleDescribeNodeCommand())
        object ZigBeeConsoleBindCommand: CommandMapper.Derived(R.string.zigbeeprotocol_bind, ZigBeeConsoleBindCommand())
        object ZigBeeConsoleUnbindCommand: CommandMapper.Derived(R.string.zigbeeprotocol_unbind, ZigBeeConsoleUnbindCommand())
        object ZigBeeConsoleBindingTableCommand: CommandMapper.Derived(R.string.zigbeeprotocol_bind_table, ZigBeeConsoleBindingTableCommand())

        object ZigBeeConsoleAttributeReadCommand: CommandMapper.Derived(R.string.zigbeeprotocol_read, ZigBeeConsoleAttributeReadCommand())
        object ZigBeeConsoleAttributeWriteCommand: CommandMapper.Derived(R.string.zigbeeprotocol_write, ZigBeeConsoleAttributeWriteCommand())

        object ZigBeeConsoleAttributeSupportedCommand: CommandMapper.Derived(R.string.zigbeeprotocol_attsupported, ZigBeeConsoleAttributeSupportedCommand())
        object ZigBeeConsoleCommandsSupportedCommand: CommandMapper.Derived(R.string.zigbeeprotocol_cmdsupported, ZigBeeConsoleCommandsSupportedCommand())

        object ZigBeeConsoleDeviceInformationCommand: CommandMapper.Derived(R.string.zigbeeprotocol_info, ZigBeeConsoleDeviceInformationCommand())
        object ZigBeeConsoleNetworkJoinCommand: CommandMapper.Derived(R.string.zigbeeprotocol_join, ZigBeeConsoleNetworkJoinCommand())
        object ZigBeeConsoleNetworkLeaveCommand: CommandMapper.Derived(R.string.zigbeeprotocol_leave, ZigBeeConsoleNetworkLeaveCommand())

        object ZigBeeConsoleReportingConfigCommand: CommandMapper.Derived(R.string.zigbeeprotocol_reporting, ZigBeeConsoleReportingConfigCommand())
        object ZigBeeConsoleReportingSubscribeCommand: CommandMapper.Derived(R.string.zigbeeprotocol_subscribe, ZigBeeConsoleReportingSubscribeCommand())
        object ZigBeeConsoleReportingUnsubscribeCommand: CommandMapper.Derived(R.string.zigbeeprotocol_unsubscribe, ZigBeeConsoleReportingUnsubscribeCommand())

        object ZigBeeConsoleInstallKeyCommand: CommandMapper.Derived(R.string.zigbeeprotocol_installkey, ZigBeeConsoleInstallKeyCommand())
        object ZigBeeConsoleLinkKeyCommand: CommandMapper.Derived(R.string.zigbeeprotocol_linkkey, ZigBeeConsoleLinkKeyCommand())

        object ZigBeeConsoleNetworkStartCommand: CommandMapper.Derived(R.string.zigbeeprotocol_netstart, ZigBeeConsoleNetworkStartCommand())
        object ZigBeeConsoleNetworkBackupCommand: CommandMapper.Derived(R.string.zigbeeprotocol_netbackup, ZigBeeConsoleNetworkBackupCommand())
        object ZigBeeConsoleNetworkDiscoveryCommand: CommandMapper.Derived(R.string.zigbeeprotocol_discovery, ZigBeeConsoleNetworkDiscoveryCommand())

        object ZigBeeConsoleOtaUpgradeCommand: CommandMapper.Derived(R.string.zigbeeprotocol_otaupgrade, ZigBeeConsoleOtaUpgradeCommand())
        object ZigBeeConsoleChannelCommand: CommandMapper.Derived(R.string.zigbeeprotocol_channel, ZigBeeConsoleChannelCommand())
    }
}

internal fun generateAvailableCommands(): Map<String, ZigBeeConsoleCommand> = mutableMapOf(
        CommandMapper.Derived.ZigBeeConsoleNodeListCommand.keyedPair,
        CommandMapper.Derived.ZigBeeConsoleDescribeEndpointCommand.keyedPair,
        CommandMapper.Derived.ZigBeeConsoleDescribeNodeCommand.keyedPair,
        CommandMapper.Derived.ZigBeeConsoleBindCommand.keyedPair,
        CommandMapper.Derived.ZigBeeConsoleUnbindCommand.keyedPair,
        CommandMapper.Derived.ZigBeeConsoleBindingTableCommand.keyedPair,

        CommandMapper.Derived.ZigBeeConsoleAttributeReadCommand.keyedPair,
        CommandMapper.Derived.ZigBeeConsoleAttributeWriteCommand.keyedPair,

        CommandMapper.Derived.ZigBeeConsoleAttributeSupportedCommand.keyedPair,
        CommandMapper.Derived.ZigBeeConsoleCommandsSupportedCommand.keyedPair,

        CommandMapper.Derived.ZigBeeConsoleDeviceInformationCommand.keyedPair,
        CommandMapper.Derived.ZigBeeConsoleNetworkJoinCommand.keyedPair,
        CommandMapper.Derived.ZigBeeConsoleNetworkLeaveCommand.keyedPair,

        CommandMapper.Derived.ZigBeeConsoleReportingConfigCommand.keyedPair,
        CommandMapper.Derived.ZigBeeConsoleReportingSubscribeCommand.keyedPair,
        CommandMapper.Derived.ZigBeeConsoleReportingUnsubscribeCommand.keyedPair,

        CommandMapper.Derived.ZigBeeConsoleInstallKeyCommand.keyedPair,
        CommandMapper.Derived.ZigBeeConsoleLinkKeyCommand.keyedPair,

        CommandMapper.Derived.ZigBeeConsoleNetworkStartCommand.keyedPair,
        CommandMapper.Derived.ZigBeeConsoleNetworkBackupCommand.keyedPair,
        CommandMapper.Derived.ZigBeeConsoleNetworkDiscoveryCommand.keyedPair,

        CommandMapper.Derived.ZigBeeConsoleOtaUpgradeCommand.keyedPair,
        CommandMapper.Derived.ZigBeeConsoleChannelCommand.keyedPair,

        CommandMapper.Custom(OnCommand()).keyedPair,
        CommandMapper.Custom(OffCommand()).keyedPair,
        CommandMapper.Custom(ColorCommand()).keyedPair,
        CommandMapper.Custom(LevelCommand()).keyedPair,

        CommandMapper.Custom(GroupAddCommand()).keyedPair,
        CommandMapper.Custom(GroupRemoveCommand()).keyedPair,
        CommandMapper.Custom(GroupListCommand()).keyedPair,

        CommandMapper.Custom(MembershipAddCommand()).keyedPair,
        CommandMapper.Custom(MembershipRemoveCommand()).keyedPair,
        CommandMapper.Custom(MembershipViewCommand()).keyedPair,
        CommandMapper.Custom(MembershipListCommand()).keyedPair,

        CommandMapper.Custom(RediscoverCommand()).keyedPair

).let { it + CommandMapper.Custom(HelpCommand(it)).keyedPair }


private val CommandMapper.keyedPair
    get() = when (this) {
        is CommandMapper.Derived -> key to consoleCommand
        is CommandMapper.Custom -> consoleCommand.command to consoleCommand
    }