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

import com.tunjid.rcswitchcontrol.common.ClosableStateHolder
import com.tunjid.rcswitchcontrol.client.ClientViewModel
import com.tunjid.rcswitchcontrol.control.ControlViewModel
import com.tunjid.rcswitchcontrol.navigation.Node
import com.tunjid.rcswitchcontrol.onboarding.NsdScanViewModel
import com.tunjid.rcswitchcontrol.server.HostViewModel
import com.tunjid.rcswitchcontrol.server.ServerViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap

inline fun <reified SM : ClosableStateHolder<*, *>> Dagger.stateMachine(
    node: Node? = null
) = appComponent.stateMachineCreator().invoke(node, SM::class) as SM


@Module
abstract class AppViewModelModule {

    @Binds
    @IntoMap
    @ViewModelKey(ControlViewModel::class)
    abstract fun bindControlViewModel(stateMachine: ControlViewModel): ClosableStateHolder<*, *>

    @Binds
    @IntoMap
    @ViewModelKey(HostViewModel::class)
    abstract fun bindPersonHostViewModel(stateMachine: HostViewModel): ClosableStateHolder<*, *>

    @Binds
    @IntoMap
    @ViewModelKey(NsdScanViewModel::class)
    abstract fun bindNsdScanViewModel(stateMachine: NsdScanViewModel): ClosableStateHolder<*, *>

    @Binds
    @IntoMap
    @ViewModelKey(ServerViewModel::class)
    abstract fun bindServerViewModel(stateMachine: ServerViewModel): ClosableStateHolder<*, *>

    @Binds
    @IntoMap
    @ViewModelKey(ClientViewModel::class)
    abstract fun bindClientViewModel(stateMachine: ClientViewModel): ClosableStateHolder<*, *>
}