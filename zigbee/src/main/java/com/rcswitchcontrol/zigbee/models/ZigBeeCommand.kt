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
import com.rcswitchcontrol.zigbee.protocol.NamedCommand
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol
import com.zsmartsystems.zigbee.zcl.protocol.ZclClusterType

data class ZigBeeCommand(
        val name: String,
        val args: List<String>
) {
    // Cannot be derived, it need to be serialized
    val key: String = ZigBeeProtocol::class.java.name

    val isInvalid: Boolean
        get() = args.isEmpty()
}

sealed class ZigBeeInput<InputT>(
        val input: InputT,
        private val namedCommand: NamedCommand
) {

    object Rediscover : ZigBeeInput<Unit>(
            input = Unit,
            namedCommand = NamedCommand.Custom.Rediscover
    )

    object Node : ZigBeeInput<Unit>(
            input = Unit,
            namedCommand = NamedCommand.Derived.DescribeNode
    )

    data class Toggle(val isOn: Boolean) : ZigBeeInput<Boolean>(
            input = isOn,
            namedCommand = if (isOn) NamedCommand.Custom.On else NamedCommand.Custom.Off
    )

    data class Level(val level: Float) : ZigBeeInput<Float>(
            input = level,
            namedCommand = NamedCommand.Custom.Level
    )

    data class Color(val rgb: Int) : ZigBeeInput<Int>(
            input = rgb,
            namedCommand = NamedCommand.Custom.Color
    )

    data class Read(val feature: ZigBeeDevice.Feature) : ZigBeeInput<ZigBeeDevice.Feature>(
            input = feature,
            namedCommand = NamedCommand.Derived.AttributeRead
    )

    internal fun from(zigBeeDevice: ZigBeeDevice): ZigBeeCommand = when (this) {
        is Rediscover -> listOf(zigBeeDevice.ieeeAddress)
        is Node -> listOf(zigBeeDevice.networkAdress)
        is Toggle -> listOf(zigBeeDevice.address(ZclClusterType.ON_OFF))
        is Level -> listOf(zigBeeDevice.address(ZclClusterType.LEVEL_CONTROL), level.toString())
        is Color -> listOf(zigBeeDevice.address(ZclClusterType.COLOR_CONTROL), rgb.red.toString(), rgb.green.toString(), rgb.blue.toString())
        is Read -> listOf(zigBeeDevice.address(feature.clusterType), feature.clusterType.id.toString()) +
                zigBeeDevice.clusterAttributeMap.getValue(feature.clusterType.id).map(Int::toString)
    }.let(this::args)

    private fun args(params: List<String>): ZigBeeCommand =
            ZigBeeCommand(name = namedCommand.name, args = listOf(namedCommand.command) + params)
}