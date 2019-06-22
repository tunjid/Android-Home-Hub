package com.tunjid.rcswitchcontrol.data.persistence

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import com.google.gson.Gson
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.data.RcSwitch

interface Deserializer<T> {

    fun deserialize(serialized: String): T

    companion object {


        val gson = Gson()


    }
}