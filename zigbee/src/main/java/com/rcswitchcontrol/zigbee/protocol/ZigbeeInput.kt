package com.rcswitchcontrol.zigbee.protocol

import androidx.core.graphics.blue
import androidx.core.graphics.red
import com.rcswitchcontrol.zigbee.commands.ColorCommand
import com.rcswitchcontrol.zigbee.commands.LevelCommand
import com.rcswitchcontrol.zigbee.commands.OffCommand
import com.rcswitchcontrol.zigbee.commands.OnCommand
import com.rcswitchcontrol.zigbee.commands.RediscoverCommand
import com.rcswitchcontrol.zigbee.models.ZigBeeCommandArgs
import com.rcswitchcontrol.zigbee.models.ZigBeeDevice
import com.zsmartsystems.zigbee.console.ZigBeeConsoleCommand
import com.zsmartsystems.zigbee.console.ZigBeeConsoleDescribeNodeCommand

sealed class ZigBeeInput<InputT>(val input: InputT, val command: ZigBeeConsoleCommand) {
    private val commandName get() = command.command

    object Rediscover : ZigBeeInput<Unit>(
            input = Unit,
            command = RediscoverCommand()
    )

    object Node : ZigBeeInput<Unit>(
            input = Unit,
            command = ZigBeeConsoleDescribeNodeCommand()
    )

    data class Toggle(val isOn: Boolean) : ZigBeeInput<Boolean>(
            input = isOn,
            command = if (isOn) OnCommand() else OffCommand()
    )

    data class Level(val level: Float) : ZigBeeInput<Float>(
            input = level,
            command = LevelCommand()
    )

    data class Color(val rgb: Int) : ZigBeeInput<Int>(
            input = rgb,
            command = ColorCommand()
    )

    internal fun from(zigBeeDevice: ZigBeeDevice): ZigBeeCommandArgs = when (this) {
        is Rediscover -> arrayOf(zigBeeDevice.ieeeAddress)
        is Node -> arrayOf(zigBeeDevice.networkAdress)
        is Toggle -> arrayOf(zigBeeDevice.zigBeeId)
        is Level -> arrayOf(zigBeeDevice.zigBeeId, level.toString())
        is Color -> arrayOf(zigBeeDevice.zigBeeId, rgb.red.toString(), rgb.blue.toString(), rgb.blue.toString())
    }.let(this::args)

    private fun args(params: Array<String>): ZigBeeCommandArgs =
            ZigBeeCommandArgs(commandName, arrayOf(commandName) + params)
}
