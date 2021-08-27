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

package com.tunjid.rcswitchcontrol.di

import android.app.Activity
import android.app.Service
import android.content.Context
import androidx.fragment.app.Fragment
import com.tunjid.rcswitchcontrol.App

val Context.dagger: Dagger get() = (applicationContext as App).dagger
val Service.dagger: Dagger get() = (application as App).dagger
val Activity.dagger: Dagger get() = (application as App).dagger
val Fragment.dagger: Dagger get() = requireActivity().dagger

var Dagger.nav
    get() = appComponent.nav.get()
    set(value) = appComponent.nav.set(value)

class Dagger(
    val appComponent: AppComponent
) {

    companion object {
        fun make(app: App): Dagger = DaggerAppComponent.builder()
            .appModule(AppModule(app))
            .build()
            .let(::Dagger)
    }
}
