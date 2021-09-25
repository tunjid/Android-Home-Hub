package com.rcswitchcontrol.zigbee.protocol

import com.rcswitchcontrol.protocols.CommonDeviceActions
import com.rcswitchcontrol.protocols.CommsProtocol
import com.rcswitchcontrol.protocols.asAction
import com.rcswitchcontrol.protocols.models.Payload
import com.rcswitchcontrol.zigbee.commands.*
import com.zsmartsystems.zigbee.console.*

internal sealed class NamedCommand(open val consoleCommand: ZigBeeConsoleCommand) {

    internal val name
        get() = when (this) {
            is Custom -> consoleCommand.command
            is Derived -> title
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

        data class Help(val commandMap: Map<String, ZigBeeConsoleCommand>) :
            Custom(HelpCommand(commandMap))
    }

    sealed class Derived(
        internal val title: String,
        override val consoleCommand: ZigBeeConsoleCommand
    ) : NamedCommand(consoleCommand) {
        object NodeList : NamedCommand.Derived("Nodes", ZigBeeConsoleNodeListCommand())
        object DescribeEndpoint :
            NamedCommand.Derived("Endpoint", ZigBeeConsoleDescribeEndpointCommand())

        object DescribeNode : NamedCommand.Derived("Node", ZigBeeConsoleDescribeNodeCommand())
        object Bind : NamedCommand.Derived("Bind", ZigBeeConsoleBindCommand())
        object Unbind : NamedCommand.Derived("Unbind", ZigBeeConsoleUnbindCommand())
        object BindingTable : NamedCommand.Derived("Bind Table", ZigBeeConsoleBindingTableCommand())

        object AttributeRead : NamedCommand.Derived("Read", ZigBeeConsoleAttributeReadCommand())
        object AttributeWrite : NamedCommand.Derived("Write", ZigBeeConsoleAttributeWriteCommand())

        object AttributeSupported :
            NamedCommand.Derived("Supported Attributes", ZigBeeConsoleAttributeSupportedCommand())

        object SupportedCommands :
            NamedCommand.Derived("Supported Commands", ZigBeeConsoleCommandsSupportedCommand())

        object DeviceInformation :
            NamedCommand.Derived("Info", ZigBeeConsoleDeviceInformationCommand())

        object NetworkJoin : NamedCommand.Derived("Join", ZigBeeConsoleNetworkJoinCommand())
        object NetworkLeave : NamedCommand.Derived("Leave", ZigBeeConsoleNetworkLeaveCommand())

        object ReportingConfig :
            NamedCommand.Derived("Reporting", ZigBeeConsoleReportingConfigCommand())

        object ReportingSubscribe :
            NamedCommand.Derived("Subscribe", ZigBeeConsoleReportingSubscribeCommand())

        object ReportingUnsubscribe :
            NamedCommand.Derived("Unsubscribe", ZigBeeConsoleReportingUnsubscribeCommand())

        object InstallKey : NamedCommand.Derived("Install Key", ZigBeeConsoleInstallKeyCommand())
        object LinkKey : NamedCommand.Derived("Link Key", ZigBeeConsoleLinkKeyCommand())

        object NetworkBackup :
            NamedCommand.Derived("Network Backup", ZigBeeConsoleNetworkBackupCommand())

        object NetworkDiscovery :
            NamedCommand.Derived("Network  Discovery", ZigBeeConsoleNetworkDiscoveryCommand())

        object OtaUpgrade : NamedCommand.Derived("OTA Discovery", ZigBeeConsoleOtaUpgradeCommand())
        object Channel : NamedCommand.Derived("Channel", ZigBeeConsoleChannelCommand())
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