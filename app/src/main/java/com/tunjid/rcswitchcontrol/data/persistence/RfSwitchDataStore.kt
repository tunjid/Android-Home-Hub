package com.tunjid.rcswitchcontrol.data.persistence

import android.content.Context
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.data.RcSwitch
import com.tunjid.rcswitchcontrol.data.RcSwitch.Companion.SWITCH_PREFS
import com.tunjid.rcswitchcontrol.data.persistence.Converter.Companion.deserializeList

class RfSwitchDataStore {

    val savedSwitches: MutableList<RcSwitch>
        get() = serializedSavedSwitches.let {
            if (it.isEmpty()) mutableListOf()
            else it.deserializeList(RcSwitch::class).toMutableList()
        }

    val serializedSavedSwitches: String
        get() = preferences.getString(SWITCHES_KEY, "")!!

    private val preferences = App.instance.getSharedPreferences(SWITCH_PREFS, Context.MODE_PRIVATE)

    fun saveSwitches(switches: List<RcSwitch>) {
        preferences.edit().putString(SWITCHES_KEY, Converter.converter.toJson(switches)).apply()
    }

    companion object {
        private const val SWITCHES_KEY = "Switches"
    }
}