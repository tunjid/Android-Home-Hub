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

import android.app.Service
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleService
import com.tunjid.rcswitchcontrol.arch.StateMachine
import com.tunjid.rcswitchcontrol.client.ClientViewModel
import com.tunjid.rcswitchcontrol.control.ControlViewModel
import com.tunjid.rcswitchcontrol.onboarding.NsdScanViewModel
import com.tunjid.rcswitchcontrol.server.HostViewModel
import com.tunjid.rcswitchcontrol.server.ServerViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap

inline fun <reified SM : StateMachine<*, *>> Fragment.stateMachine() = lazy {
    val path = generateSequence(parentFragment, Fragment::getParentFragment)
        .mapNotNull(Fragment::getTag)
        .joinToString(separator = "/")
    dagger.stateMachine<SM>(path)
}

inline fun <reified SM : StateMachine<*, *>> Fragment.rootStateMachine() = lazy {
    dagger.stateMachine<SM>("Root")
}

inline fun <reified SM : StateMachine<*, *>> FragmentActivity.stateMachine() = lazy {
    dagger.stateMachine<SM>(this.javaClass.simpleName)
}

inline fun <reified SM : StateMachine<*, *>> Service.stateMachine() = lazy {
    dagger.stateMachine<SM>(this.javaClass.simpleName)
}

inline fun <reified SM : StateMachine<*, *>> Dagger.stateMachine(
    path: String
) = appComponent.stateMachineCreator().invoke(path, SM::class) as SM

@Module
abstract class AppViewModelModule {

    @Binds
    @IntoMap
    @ViewModelKey(ControlViewModel::class)
    abstract fun bindControlViewModel(stateMachine: ControlViewModel): StateMachine<*, *>

    @Binds
    @IntoMap
    @ViewModelKey(HostViewModel::class)
    abstract fun bindPersonHostViewModel(stateMachine: HostViewModel): StateMachine<*, *>

    @Binds
    @IntoMap
    @ViewModelKey(NsdScanViewModel::class)
    abstract fun bindNsdScanViewModel(stateMachine: NsdScanViewModel): StateMachine<*, *>

    @Binds
    @IntoMap
    @ViewModelKey(ServerViewModel::class)
    abstract fun bindServerViewModel(stateMachine: ServerViewModel): StateMachine<*, *>

    @Binds
    @IntoMap
    @ViewModelKey(ClientViewModel::class)
    abstract fun bindClientViewModel(stateMachine: ClientViewModel): StateMachine<*, *>
}