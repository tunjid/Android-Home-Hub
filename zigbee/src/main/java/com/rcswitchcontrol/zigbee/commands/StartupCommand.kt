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

package com.rcswitchcontrol.zigbee.commands

import com.rcswitchcontrol.zigbee.R
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.zsmartsystems.zigbee.ExtendedPanId
import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import com.zsmartsystems.zigbee.transport.DeviceType
import com.zsmartsystems.zigbee.transport.TransportConfig
import com.zsmartsystems.zigbee.transport.TransportConfigOption
import java.io.PrintStream


/**
 * Switches a device on.
 */
class StartupCommand : AbsZigBeeCommand() {
    override val args: String = "PANID EPANID"

    override fun getCommand(): String = ContextProvider.appContext.getString(R.string.zigbeeprotocol_formnet)

    override fun getDescription(): String = "Forms a network"

    @Throws(Exception::class)
    override fun process(networkManager: ZigBeeNetworkManager, args: Array<out String>, out: PrintStream) {
        args.expect(3)
//
//        networkManager.zigBeeTransport.apply {
//            updateTransportConfig(TransportConfig().apply {
//                addOption(TransportConfigOption.DEVICE_TYPE, DeviceType.COORDINATOR)
//            })
////            out.println("ZigBees status is: ${startup(true)}")
//            out.println("Reinitialize: ${networkManager.reinitialize()}")
//            out.println("start up: ${networkManager.startup(true)}")
//        }

        val transportOptions = TransportConfig()

        networkManager.setZigBeePanId(args[1].toInt())
        networkManager.setZigBeeExtendedPanId(ExtendedPanId(args[2]))
        transportOptions.addOption(TransportConfigOption.DEVICE_TYPE, DeviceType.COORDINATOR)
        networkManager.getZigBeeTransport().updateTransportConfig(transportOptions)
        out.println("start up: ${networkManager.startup(true)}")
    }
}