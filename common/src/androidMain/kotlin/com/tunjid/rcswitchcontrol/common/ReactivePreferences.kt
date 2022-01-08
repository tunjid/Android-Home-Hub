/*
 * Copyright (c) 2017, 2018, 2019 Adetunji Dahunsi.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.tunjid.rcswitchcontrol.common

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

private val listeners: MutableSet<SharedPreferences.OnSharedPreferenceChangeListener> = mutableSetOf()

class ReactivePreferences(
    val preferences: SharedPreferences,
) {
    val monitor = callbackFlow<String> {
        val prefs = preferences
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            channel.trySend(key)
        }
        listener
            .also(prefs::registerOnSharedPreferenceChangeListener)
            .let(listeners::add)
        awaitClose {
            listener
                .also(prefs::unregisterOnSharedPreferenceChangeListener)
                .let(listeners::remove)
        }
    }
}

class ReactivePreference<T : Any>(
    private val reactivePreferences: ReactivePreferences,
    private val key: String,
    private val default: T,
    private val onSet: ((T) -> Unit)? = null
) {

    @Suppress("UNCHECKED_CAST")
    var value: T
        get() = with(reactivePreferences.preferences) {
            when (default) {
                is String -> getString(key, default)
                is Int -> getInt(key, default)
                is Long -> getLong(key, default)
                is Float -> getFloat(key, default)
                is Boolean -> getBoolean(key, default)
                is Set<*> -> HashSet(getStringSet(key, emptySet())?.filterNotNull()
                    ?: emptySet<String>())
                else -> throw IllegalArgumentException("Uhh what are you doing?")
            }
        } as T
        set(value) = with(reactivePreferences.preferences.edit()) {
            when (value) {
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Float -> putFloat(key, value)
                is Boolean -> putBoolean(key, value)
                is Set<*> -> putStringSet(key, value.map(Any?::toString).toSet())
                else -> throw IllegalArgumentException("Uhh what are you doing?")
            }
            apply()
            onSet?.invoke(value)
        }

    /**
     * A reference to the setter. Same as assigning to the value, but as a method reference for
     * being stable for reference equality checks
     */
    val setter = ::value::set

    val monitor: Flow<T> = reactivePreferences.monitor
        .filter(key::equals)
        .map { value }
        .onStart { emit(value) }
        .flowOn(Dispatchers.IO)
}
