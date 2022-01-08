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

package com.rcswitchcontrol.zigbee.persistence

import android.content.Context
import com.google.gson.Gson
import com.rcswitchcontrol.zigbee.models.device
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.zsmartsystems.zigbee.IeeeAddress
import com.zsmartsystems.zigbee.database.ZigBeeNetworkDataStore
import com.zsmartsystems.zigbee.database.ZigBeeNodeDao

private val gson = Gson()

class ZigBeeDataStore(private val networkId: String) : ZigBeeNetworkDataStore {

    val hasNoDevices: Boolean
        get() = preferences(networkId).all.isEmpty()

    val savedDevices
        get() = readNetworkNodes()
            .map(::readNode)
            .mapNotNull(ZigBeeNodeDao::device)

    override fun removeNode(address: IeeeAddress) =
        preferences(networkId).edit().remove(address.toString()).apply()

    override fun readNetworkNodes(): MutableSet<IeeeAddress> =
        preferences(networkId).all.keys.map { IeeeAddress(it.substring(0, 16)) }.toMutableSet()

    override fun readNode(address: IeeeAddress): ZigBeeNodeDao =
        gson.fromJson(preferences(networkId).getString(address.toString(), "")!!, ZigBeeNodeDao::class.java)

    override fun writeNode(node: ZigBeeNodeDao) {
        preferences(networkId).edit().putString(node.ieeeAddress.toString(), gson.toJson(node)).apply()
    }

    private fun preferences(key: String) = ContextProvider.appContext.getSharedPreferences(key, Context.MODE_PRIVATE)
}