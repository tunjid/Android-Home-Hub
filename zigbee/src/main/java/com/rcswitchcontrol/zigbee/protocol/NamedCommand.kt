package com.rcswitchcontrol.zigbee.protocol

import com.rcswitchcontrol.zigbee.R
import com.rcswitchcontrol.zigbee.commands.*
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.zsmartsystems.zigbee.console.*

internal sealed class NamedCommand(open val consoleCommand: ZigBeeConsoleCommand) {

    internal val name
        get() = when (this) {
            is Custom -> consoleCommand.command
            is Derived -> ContextProvider.appContext.getString(stringRes)
        }

    internal val command
        get() = consoleCommand.command

    data class Custom(override val consoleCommand: AbsZigBeeCommand) : NamedCommand(consoleCommand)
    sealed class Derived(internal val stringRes: Int, override val consoleCommand: ZigBeeConsoleCommand) : NamedCommand(consoleCommand) {
        object ZigBeeConsoleNodeListCommand : NamedCommand.Derived(R.string.zigbeeprotocol_nodes, ZigBeeConsoleNodeListCommand())
        object ZigBeeConsoleDescribeEndpointCommand : NamedCommand.Derived(R.string.zigbeeprotocol_endpoint, ZigBeeConsoleDescribeEndpointCommand())
        object ZigBeeConsoleDescribeNodeCommand : NamedCommand.Derived(R.string.zigbeeprotocol_node, ZigBeeConsoleDescribeNodeCommand())
        object ZigBeeConsoleBindCommand : NamedCommand.Derived(R.string.zigbeeprotocol_bind, ZigBeeConsoleBindCommand())
        object ZigBeeConsoleUnbindCommand : NamedCommand.Derived(R.string.zigbeeprotocol_unbind, ZigBeeConsoleUnbindCommand())
        object ZigBeeConsoleBindingTableCommand : NamedCommand.Derived(R.string.zigbeeprotocol_bind_table, ZigBeeConsoleBindingTableCommand())

        object ZigBeeConsoleAttributeReadCommand : NamedCommand.Derived(R.string.zigbeeprotocol_read, ZigBeeConsoleAttributeReadCommand())
        object ZigBeeConsoleAttributeWriteCommand : NamedCommand.Derived(R.string.zigbeeprotocol_write, ZigBeeConsoleAttributeWriteCommand())

        object ZigBeeConsoleAttributeSupportedCommand : NamedCommand.Derived(R.string.zigbeeprotocol_attsupported, ZigBeeConsoleAttributeSupportedCommand())
        object ZigBeeConsoleCommandsSupportedCommand : NamedCommand.Derived(R.string.zigbeeprotocol_cmdsupported, ZigBeeConsoleCommandsSupportedCommand())

        object ZigBeeConsoleDeviceInformationCommand : NamedCommand.Derived(R.string.zigbeeprotocol_info, ZigBeeConsoleDeviceInformationCommand())
        object ZigBeeConsoleNetworkJoinCommand : NamedCommand.Derived(R.string.zigbeeprotocol_join, ZigBeeConsoleNetworkJoinCommand())
        object ZigBeeConsoleNetworkLeaveCommand : NamedCommand.Derived(R.string.zigbeeprotocol_leave, ZigBeeConsoleNetworkLeaveCommand())

        object ZigBeeConsoleReportingConfigCommand : NamedCommand.Derived(R.string.zigbeeprotocol_reporting, ZigBeeConsoleReportingConfigCommand())
        object ZigBeeConsoleReportingSubscribeCommand : NamedCommand.Derived(R.string.zigbeeprotocol_subscribe, ZigBeeConsoleReportingSubscribeCommand())
        object ZigBeeConsoleReportingUnsubscribeCommand : NamedCommand.Derived(R.string.zigbeeprotocol_unsubscribe, ZigBeeConsoleReportingUnsubscribeCommand())

        object ZigBeeConsoleInstallKeyCommand : NamedCommand.Derived(R.string.zigbeeprotocol_installkey, ZigBeeConsoleInstallKeyCommand())
        object ZigBeeConsoleLinkKeyCommand : NamedCommand.Derived(R.string.zigbeeprotocol_linkkey, ZigBeeConsoleLinkKeyCommand())

        object ZigBeeConsoleNetworkStartCommand : NamedCommand.Derived(R.string.zigbeeprotocol_netstart, ZigBeeConsoleNetworkStartCommand())
        object ZigBeeConsoleNetworkBackupCommand : NamedCommand.Derived(R.string.zigbeeprotocol_netbackup, ZigBeeConsoleNetworkBackupCommand())
        object ZigBeeConsoleNetworkDiscoveryCommand : NamedCommand.Derived(R.string.zigbeeprotocol_discovery, ZigBeeConsoleNetworkDiscoveryCommand())

        object ZigBeeConsoleOtaUpgradeCommand : NamedCommand.Derived(R.string.zigbeeprotocol_otaupgrade, ZigBeeConsoleOtaUpgradeCommand())
        object ZigBeeConsoleChannelCommand : NamedCommand.Derived(R.string.zigbeeprotocol_channel, ZigBeeConsoleChannelCommand())
    }
}

internal fun generateAvailableCommands(): Map<String, NamedCommand> = mutableMapOf(
        NamedCommand.Derived.ZigBeeConsoleNodeListCommand.keyedPair,
        NamedCommand.Derived.ZigBeeConsoleDescribeEndpointCommand.keyedPair,
        NamedCommand.Derived.ZigBeeConsoleDescribeNodeCommand.keyedPair,
        NamedCommand.Derived.ZigBeeConsoleBindCommand.keyedPair,
        NamedCommand.Derived.ZigBeeConsoleUnbindCommand.keyedPair,
        NamedCommand.Derived.ZigBeeConsoleBindingTableCommand.keyedPair,

        NamedCommand.Derived.ZigBeeConsoleAttributeReadCommand.keyedPair,
        NamedCommand.Derived.ZigBeeConsoleAttributeWriteCommand.keyedPair,

        NamedCommand.Derived.ZigBeeConsoleAttributeSupportedCommand.keyedPair,
        NamedCommand.Derived.ZigBeeConsoleCommandsSupportedCommand.keyedPair,

        NamedCommand.Derived.ZigBeeConsoleDeviceInformationCommand.keyedPair,
        NamedCommand.Derived.ZigBeeConsoleNetworkJoinCommand.keyedPair,
        NamedCommand.Derived.ZigBeeConsoleNetworkLeaveCommand.keyedPair,

        NamedCommand.Derived.ZigBeeConsoleReportingConfigCommand.keyedPair,
        NamedCommand.Derived.ZigBeeConsoleReportingSubscribeCommand.keyedPair,
        NamedCommand.Derived.ZigBeeConsoleReportingUnsubscribeCommand.keyedPair,

        NamedCommand.Derived.ZigBeeConsoleInstallKeyCommand.keyedPair,
        NamedCommand.Derived.ZigBeeConsoleLinkKeyCommand.keyedPair,

        NamedCommand.Derived.ZigBeeConsoleNetworkStartCommand.keyedPair,
        NamedCommand.Derived.ZigBeeConsoleNetworkBackupCommand.keyedPair,
        NamedCommand.Derived.ZigBeeConsoleNetworkDiscoveryCommand.keyedPair,

        NamedCommand.Derived.ZigBeeConsoleOtaUpgradeCommand.keyedPair,
        NamedCommand.Derived.ZigBeeConsoleChannelCommand.keyedPair,

        NamedCommand.Custom(OnCommand()).keyedPair,
        NamedCommand.Custom(OffCommand()).keyedPair,
        NamedCommand.Custom(ColorCommand()).keyedPair,
        NamedCommand.Custom(LevelCommand()).keyedPair,

        NamedCommand.Custom(GroupAddCommand()).keyedPair,
        NamedCommand.Custom(GroupRemoveCommand()).keyedPair,
        NamedCommand.Custom(GroupListCommand()).keyedPair,

        NamedCommand.Custom(MembershipAddCommand()).keyedPair,
        NamedCommand.Custom(MembershipRemoveCommand()).keyedPair,
        NamedCommand.Custom(MembershipViewCommand()).keyedPair,
        NamedCommand.Custom(MembershipListCommand()).keyedPair,

        NamedCommand.Custom(RediscoverCommand()).keyedPair

).let { commands -> commands + NamedCommand.Custom(HelpCommand(commands.mapValues { it.value.consoleCommand })).keyedPair }


private val NamedCommand.keyedPair
    get() = name to this