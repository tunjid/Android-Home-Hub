/*
 * MIT License
 *
 * Copyright (c) 2019 Adetunji Dahunsi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.rcswitchcontrol.zigbee.models

import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import com.rcswitchcontrol.zigbee.commands.ColorCommand
import com.rcswitchcontrol.zigbee.commands.LevelCommand
import com.rcswitchcontrol.zigbee.commands.OffCommand
import com.rcswitchcontrol.zigbee.commands.OnCommand
import com.rcswitchcontrol.zigbee.commands.RediscoverCommand
import com.rcswitchcontrol.zigbee.protocol.CommandMapper
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol

data class ZigBeeCommand(
        val command: String,
        val args: List<String>
) {
    // Cannot be derived, it need to be serialized
    val key: String = ZigBeeProtocol::class.java.name

    val isInvalid: Boolean
        get() = args.isEmpty()
}

sealed class ZigBeeInput<InputT>(
        val input: InputT,
        private val commandMapper: CommandMapper
) {

    private val key
        get() = when (commandMapper) {
            is CommandMapper.Derived -> commandMapper.key
            is CommandMapper.Custom -> commandMapper.consoleCommand.command
        }

    private val commandName
        get() = when (commandMapper) {
            is CommandMapper.Derived -> commandMapper.consoleCommand.command
            is CommandMapper.Custom -> commandMapper.consoleCommand.command
        }

    object Rediscover : ZigBeeInput<Unit>(
            input = Unit,
            commandMapper = CommandMapper.Custom(RediscoverCommand())
    )

    object Node : ZigBeeInput<Unit>(
            input = Unit,
            commandMapper = CommandMapper.Derived.ZigBeeConsoleDescribeNodeCommand
    )

    data class Toggle(val isOn: Boolean) : ZigBeeInput<Boolean>(
            input = isOn,
            commandMapper = CommandMapper.Custom(if (isOn) OnCommand() else OffCommand())
    )

    data class Level(val level: Float) : ZigBeeInput<Float>(
            input = level,
            commandMapper = CommandMapper.Custom(LevelCommand())
    )

    data class Color(val rgb: Int) : ZigBeeInput<Int>(
            input = rgb,
            commandMapper = CommandMapper.Custom(ColorCommand())
    )

    internal fun from(zigBeeDevice: ZigBeeDevice): ZigBeeCommand = when (this) {
        is Rediscover -> listOf(zigBeeDevice.ieeeAddress)
        is Node -> listOf(zigBeeDevice.networkAdress)
        is Toggle -> listOf(zigBeeDevice.zigBeeId)
        is Level -> listOf(zigBeeDevice.zigBeeId, level.toString())
        is Color -> listOf(zigBeeDevice.zigBeeId, rgb.red.toString(), rgb.green.toString(), rgb.blue.toString())
    }.let(this::args)

    private fun args(params: List<String>): ZigBeeCommand =
            ZigBeeCommand(command = key, args = listOf(commandName) + params)
}