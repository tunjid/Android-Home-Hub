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
import com.rcswitchcontrol.protocols.asAction
import com.rcswitchcontrol.zigbee.protocol.NamedCommand
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol
import com.rcswitchcontrol.zigbee.protocol.ZigBeeProtocol.Companion.zigBeePayload
import com.tunjid.rcswitchcontrol.common.serialize
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

    data class Read(val feature: ZigBeeNode.Feature) : ZigBeeInput<ZigBeeNode.Feature>(
            input = feature,
            namedCommand = NamedCommand.Custom.DeviceAttributes
    )

    internal fun commandFor(zigBeeNode: ZigBeeNode): ZigBeeCommand = when (this) {
        is Rediscover -> listOf(zigBeeNode.ieeeAddress)
        is Node -> listOf(zigBeeNode.networkAdress)
        is Toggle -> listOf(zigBeeNode.address(ZclClusterType.ON_OFF))
        is Level -> listOf(zigBeeNode.address(ZclClusterType.LEVEL_CONTROL), level.toString())
        is Color -> listOf(zigBeeNode.address(ZclClusterType.COLOR_CONTROL), rgb.red.toString(), rgb.green.toString(), rgb.blue.toString())
        is Read -> listOf(
                zigBeeNode.address(feature.clusterType), feature.clusterType.id.toString())
                .plus(zigBeeNode.clusterAttributeMap.getValue(feature.clusterType.id)
                        .filter(feature.descriptors.map(ZigBeeAttribute.Descriptor::attributeId)::contains)
                        .map(Int::toString)
                )
    }.let(this::args)

    private fun args(params: List<String>): ZigBeeCommand =
            ZigBeeCommand(name = namedCommand.name, args = listOf(namedCommand.command) + params)
}

val ZigBeeCommand.payload
    get() = zigBeePayload(
            action = name.asAction,
            data = serialize()
    )