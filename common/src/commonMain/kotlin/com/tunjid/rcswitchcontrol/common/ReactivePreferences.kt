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

import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneNotNull
import com.tunjid.rcswitchcontrol.common.data.StringKeyValueDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

class ReactivePreference(
    driver: SqlDriver,
    private val key: String,
) {

    private val queries = StringKeyValueDatabase(driver).stringKeyValueQueries

    var value: String?
        get() = queries
            .select(id = key)
            .executeAsOneOrNull()
            ?.entry
        set(value) = queries
            .add(id = key, entry = value)


    /**
     * A reference to the setter. Same as assigning to the value, but as a method reference for
     * being stable for reference equality checks
     */
    val setter = ::value::set

    val monitor: Flow<String> = queries.select(id = key)
        .asFlow()
        .mapToOneNotNull()
        .map { it.entry }
        .filterNotNull()
}
