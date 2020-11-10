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
import android.util.Log
import com.tunjid.rcswitchcontrol.common.deserialize
import com.tunjid.rcswitchcontrol.common.serialize
import com.tunjid.rcswitchcontrol.common.ContextProvider
import com.zsmartsystems.zigbee.IeeeAddress
import com.zsmartsystems.zigbee.database.ZigBeeNetworkDataStore
import com.zsmartsystems.zigbee.database.ZigBeeNodeDao

class ZigBeeDataStore(private val networkId: String) : ZigBeeNetworkDataStore {

    val hasNoDevices: Boolean
        get() = preferences(networkId).all.isEmpty()

    override fun removeNode(address: IeeeAddress) =
            preferences(networkId).edit().remove(address.toString()).apply()

    override fun readNetworkNodes(): MutableSet<IeeeAddress> =
            preferences(networkId).all.keys.map { IeeeAddress(it.substring(0, 16)) }.toMutableSet()

    override fun readNode(address: IeeeAddress): ZigBeeNodeDao =
            preferences(networkId).getString(address.toString(), "")!!.deserialize(ZigBeeNodeDao::class)

    override fun writeNode(node: ZigBeeNodeDao) =
            preferences(networkId).edit().putString(node.ieeeAddress.toString(), node.serialize()).apply()

    private fun preferences(key: String) = ContextProvider.appContext.getSharedPreferences(key, Context.MODE_PRIVATE)

}