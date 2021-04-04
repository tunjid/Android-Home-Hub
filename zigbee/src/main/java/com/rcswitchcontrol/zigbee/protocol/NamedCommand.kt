package com.rcswitchcontrol.zigbee.protocol

import com.rcswitchcontrol.protocols.CommonDeviceActions
import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.asAction
import com.rcswitchcontrol.protocols.models.Payload
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

    sealed class Custom(override val consoleCommand: PayloadPublishingCommand) : NamedCommand(consoleCommand) {

        val action get() = command.asAction

        object On : Custom(OnCommand())
        object Off : Custom(OffCommand())
        object Color : Custom(ColorCommand())
        object Level : Custom(LevelCommand())
        object GroupAdd : Custom(GroupAddCommand())
        object GroupRemove : Custom(GroupRemoveCommand())
        object GroupList : Custom(GroupListCommand())
        object MembershipAdd : Custom(MembershipAddCommand())
        object MembershipRemove : Custom(MembershipRemoveCommand())
        object MembershipView : Custom(MembershipViewCommand())
        object MembershipList : Custom(MembershipListCommand())
        object Rediscover : Custom(RediscoverCommand())
        object DeviceAttributes : Custom(ReadDeviceAttributesCommand())

        data class Help(val commandMap: Map<String, ZigBeeConsoleCommand>) : Custom(HelpCommand(commandMap))
    }

    sealed class Derived(internal val stringRes: Int, override val consoleCommand: ZigBeeConsoleCommand) : NamedCommand(consoleCommand) {
        object NodeList : NamedCommand.Derived(R.string.zigbeeprotocol_nodes, ZigBeeConsoleNodeListCommand())
        object DescribeEndpoint : NamedCommand.Derived(R.string.zigbeeprotocol_endpoint, ZigBeeConsoleDescribeEndpointCommand())
        object DescribeNode : NamedCommand.Derived(R.string.zigbeeprotocol_node, ZigBeeConsoleDescribeNodeCommand())
        object Bind : NamedCommand.Derived(R.string.zigbeeprotocol_bind, ZigBeeConsoleBindCommand())
        object Unbind : NamedCommand.Derived(R.string.zigbeeprotocol_unbind, ZigBeeConsoleUnbindCommand())
        object BindingTable : NamedCommand.Derived(R.string.zigbeeprotocol_bind_table, ZigBeeConsoleBindingTableCommand())

        object AttributeRead : NamedCommand.Derived(R.string.zigbeeprotocol_read, ZigBeeConsoleAttributeReadCommand())
        object AttributeWrite : NamedCommand.Derived(R.string.zigbeeprotocol_write, ZigBeeConsoleAttributeWriteCommand())

        object AttributeSupported : NamedCommand.Derived(R.string.zigbeeprotocol_attsupported, ZigBeeConsoleAttributeSupportedCommand())
        object SupportedCommands : NamedCommand.Derived(R.string.zigbeeprotocol_cmdsupported, ZigBeeConsoleCommandsSupportedCommand())

        object DeviceInformation : NamedCommand.Derived(R.string.zigbeeprotocol_info, ZigBeeConsoleDeviceInformationCommand())
        object NetworkJoin : NamedCommand.Derived(R.string.zigbeeprotocol_join, ZigBeeConsoleNetworkJoinCommand())
        object NetworkLeave : NamedCommand.Derived(R.string.zigbeeprotocol_leave, ZigBeeConsoleNetworkLeaveCommand())

        object ReportingConfig : NamedCommand.Derived(R.string.zigbeeprotocol_reporting, ZigBeeConsoleReportingConfigCommand())
        object ReportingSubscribe : NamedCommand.Derived(R.string.zigbeeprotocol_subscribe, ZigBeeConsoleReportingSubscribeCommand())
        object ReportingUnsubscribe : NamedCommand.Derived(R.string.zigbeeprotocol_unsubscribe, ZigBeeConsoleReportingUnsubscribeCommand())

        object InstallKey : NamedCommand.Derived(R.string.zigbeeprotocol_installkey, ZigBeeConsoleInstallKeyCommand())
        object LinkKey : NamedCommand.Derived(R.string.zigbeeprotocol_linkkey, ZigBeeConsoleLinkKeyCommand())

        object NetworkBackup : NamedCommand.Derived(R.string.zigbeeprotocol_netbackup, ZigBeeConsoleNetworkBackupCommand())
        object NetworkDiscovery : NamedCommand.Derived(R.string.zigbeeprotocol_discovery, ZigBeeConsoleNetworkDiscoveryCommand())

        object OtaUpgrade : NamedCommand.Derived(R.string.zigbeeprotocol_otaupgrade, ZigBeeConsoleOtaUpgradeCommand())
        object Channel : NamedCommand.Derived(R.string.zigbeeprotocol_channel, ZigBeeConsoleChannelCommand())
    }
}

internal val ZigBeeProtocol.Companion.availableCommands: Map<CommsProtocol.Action, NamedCommand> by lazy {
    val commands = mutableMapOf(
            NamedCommand.Derived.NodeList.keyedPair,
            NamedCommand.Derived.DescribeEndpoint.keyedPair,
            NamedCommand.Derived.DescribeNode.keyedPair,
            NamedCommand.Derived.Bind.keyedPair,
            NamedCommand.Derived.Unbind.keyedPair,
            NamedCommand.Derived.BindingTable.keyedPair,

            NamedCommand.Derived.AttributeRead.keyedPair,
            NamedCommand.Derived.AttributeWrite.keyedPair,

            NamedCommand.Derived.AttributeSupported.keyedPair,
            NamedCommand.Derived.SupportedCommands.keyedPair,

            NamedCommand.Derived.DeviceInformation.keyedPair,
            NamedCommand.Derived.NetworkJoin.keyedPair,
            NamedCommand.Derived.NetworkLeave.keyedPair,

            NamedCommand.Derived.ReportingConfig.keyedPair,
            NamedCommand.Derived.ReportingSubscribe.keyedPair,
            NamedCommand.Derived.ReportingUnsubscribe.keyedPair,

            NamedCommand.Derived.InstallKey.keyedPair,
            NamedCommand.Derived.LinkKey.keyedPair,

            NamedCommand.Derived.NetworkBackup.keyedPair,
            NamedCommand.Derived.NetworkDiscovery.keyedPair,

            NamedCommand.Derived.OtaUpgrade.keyedPair,
            NamedCommand.Derived.Channel.keyedPair,

            NamedCommand.Custom.On.keyedPair,
            NamedCommand.Custom.Off.keyedPair,
            NamedCommand.Custom.Color.keyedPair,
            NamedCommand.Custom.Level.keyedPair,

            NamedCommand.Custom.GroupAdd.keyedPair,
            NamedCommand.Custom.GroupRemove.keyedPair,
            NamedCommand.Custom.GroupList.keyedPair,

            NamedCommand.Custom.MembershipAdd.keyedPair,
            NamedCommand.Custom.MembershipRemove.keyedPair,
            NamedCommand.Custom.MembershipView.keyedPair,
            NamedCommand.Custom.MembershipList.keyedPair,

            NamedCommand.Custom.Rediscover.keyedPair,
            NamedCommand.Custom.DeviceAttributes.keyedPair,
            )

    val commandsMap = commands
            .map { it.key.value to it.value.consoleCommand }
            .toMap()

    commands + NamedCommand.Custom.Help(commandsMap).keyedPair
}


private val NamedCommand.keyedPair
    get() = CommsProtocol.Action(name) to this

internal fun Payload.appendZigBeeCommands() = apply {
    addCommand(CommsProtocol.resetAction)
    addCommand(CommonDeviceActions.refreshDevicesAction)
    ZigBeeProtocol.availableCommands.keys.forEach(::addCommand)
}