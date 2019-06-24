package com.tunjid.rcswitchcontrol.data

import android.os.Parcel
import android.os.Parcelable
import com.tunjid.rcswitchcontrol.data.persistence.Deserializer

class ZigBeeLight(
        val ieeeAddress: String,
        val networkAdress: String,
        val endpoint: String
) : Parcelable {

    constructor(parcel: Parcel) : this(
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readString()!!)

    fun serialize(): String = Deserializer.gson.toJson(this)

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(ieeeAddress)
        parcel.writeString(networkAdress)
        parcel.writeString(endpoint)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR :
            Deserializer<ZigBeeLight>,
            Parcelable.Creator<ZigBeeLight> {

        override fun deserialize(serialized: String): ZigBeeLight = Deserializer.gson.fromJson(serialized, ZigBeeLight::class.java)

        override fun createFromParcel(parcel: Parcel): ZigBeeLight = ZigBeeLight(parcel)

        override fun newArray(size: Int): Array<ZigBeeLight?> = arrayOfNulls(size)
    }
}