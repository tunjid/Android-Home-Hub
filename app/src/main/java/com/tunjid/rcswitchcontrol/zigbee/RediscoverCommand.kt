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

package com.tunjid.rcswitchcontrol.zigbee

import com.rcswitchcontrol.protocols.ContextProvider
import com.tunjid.rcswitchcontrol.R
import com.zsmartsystems.zigbee.IeeeAddress
import com.zsmartsystems.zigbee.ZigBeeNetworkManager
import java.io.PrintStream

/**
 * Rediscover a node from its IEEE address.
 */
class RediscoverCommand : AbsZigBeeCommand() {
    override val args: String = "IEEEADDRESS"

    override fun getCommand(): String = ContextProvider.appContext.getString(R.string.zigbeeprotocol_rediscover)

    override fun getDescription(): String = "Rediscover a node from its IEEE address."

    @Throws(Exception::class)
    override fun process(networkManager: ZigBeeNetworkManager, args: Array<out String>, out: PrintStream) {
        if (args.size != 2) throw IllegalArgumentException("Invalid command arguments")

        val address = IeeeAddress(args[1])

        out.push("Sending rediscovery request for address $address")
        networkManager.rediscoverNode(address)
    }
}