package com.tunjid.rcswitchcontrol.data.persistence

import android.content.Context
import com.google.gson.Gson
import com.tunjid.rcswitchcontrol.App
import com.zsmartsystems.zigbee.IeeeAddress
import com.zsmartsystems.zigbee.database.ZigBeeNetworkDataStore
import com.zsmartsystems.zigbee.database.ZigBeeNodeDao

class ZigbeeDataStore(val networkId : String) : ZigBeeNetworkDataStore {
    override fun removeNode(address: IeeeAddress?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun readNetworkNodes(): MutableSet<IeeeAddress> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun readNode(address: IeeeAddress?): ZigBeeNodeDao {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun writeNode(node: ZigBeeNodeDao) =
            prefrences().edit().putString(node.ieeeAddress.toString(), marshal.toJson(node)).apply()

    fun prefrences() = App.instance.getSharedPreferences(ZIGBEE_KEY, Context.MODE_PRIVATE)

    companion object {
        private const val ZIGBEE_KEY = "Zigbee"
        private val marshal = Gson()
    }
}