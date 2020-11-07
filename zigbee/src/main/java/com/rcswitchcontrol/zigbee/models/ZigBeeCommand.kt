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
import androidx.core.graphics.red
import com.rcswitchcontrol.zigbee.commands.ColorCommand
import com.rcswitchcontrol.zigbee.commands.LevelCommand
import com.rcswitchcontrol.zigbee.commands.OffCommand
import com.rcswitchcontrol.zigbee.commands.OnCommand
import com.rcswitchcontrol.zigbee.commands.RediscoverCommand
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol
import com.zsmartsystems.zigbee.console.ZigBeeConsoleCommand
import com.zsmartsystems.zigbee.console.ZigBeeConsoleDescribeNodeCommand

data class ZigBeeCommand(
        val command: String,
        val args: List<String>
) {

    val key: String = ZigBeeProtocol::class.java.name

    val isInvalid: Boolean
        get() = args.isEmpty()
}

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

    internal fun from(zigBeeDevice: ZigBeeDevice): ZigBeeCommand = when (this) {
        is Rediscover -> listOf(zigBeeDevice.ieeeAddress)
        is Node -> listOf(zigBeeDevice.networkAdress)
        is Toggle -> listOf(zigBeeDevice.zigBeeId)
        is Level -> listOf(zigBeeDevice.zigBeeId, level.toString())
        is Color -> listOf(zigBeeDevice.zigBeeId, rgb.red.toString(), rgb.blue.toString(), rgb.blue.toString())
    }.let(this::args)

    private fun args(params: List<String>): ZigBeeCommand =
            ZigBeeCommand(commandName, listOf(commandName) + params)
}