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

package com.tunjid.rcswitchcontrol.data.persistence

import android.content.Context
import com.rcswitchcontrol.protocols.ContextProvider
import com.rcswitchcontrol.protocols.persistence.Converter
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.data.RfSwitch
import com.tunjid.rcswitchcontrol.data.RfSwitch.Companion.SWITCH_PREFS
import com.rcswitchcontrol.protocols.persistence.deserializeList

class RfSwitchDataStore {

    val savedSwitches: MutableList<RfSwitch>
        get() = serializedSavedSwitches.let {
            if (it.isEmpty()) mutableListOf()
            else it.deserializeList(RfSwitch::class).toMutableList()
        }

    val serializedSavedSwitches: String
        get() = preferences.getString(SWITCHES_KEY, "")!!

    private val preferences = ContextProvider.appContext.getSharedPreferences(SWITCH_PREFS, Context.MODE_PRIVATE)

    fun saveSwitches(switches: List<RfSwitch>) {
        preferences.edit().putString(SWITCHES_KEY, Converter.converter.toJson(switches)).apply()
    }

    companion object {
        private const val SWITCHES_KEY = "Switches"
    }
}