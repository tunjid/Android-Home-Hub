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

import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelLazy
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.tunjid.fingergestures.di.ViewModelCreators
import com.tunjid.fingergestures.di.ViewModelFactory
import com.tunjid.fingergestures.di.ViewModelKey
import com.tunjid.rcswitchcontrol.client.ClientViewModel
import com.tunjid.rcswitchcontrol.utils.LifecycleViewModelStoreProvider
import com.tunjid.rcswitchcontrol.server.ServerViewModel
import com.tunjid.rcswitchcontrol.control.ControlViewModel
import com.tunjid.rcswitchcontrol.server.HostViewModel
import com.tunjid.rcswitchcontrol.onboarding.NsdScanViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import javax.inject.Inject
import javax.inject.Singleton

inline fun <reified VM : ViewModel> Fragment.viewModelFactory(
    noinline ownerProducer: () -> ViewModelStoreOwner = { this }
) = viewModels<VM>(ownerProducer = ownerProducer) { dagger.viewModelFactory }

inline fun <reified VM : ViewModel> Fragment.activityViewModelFactory() =
    activityViewModels<VM> { dagger.viewModelFactory }

inline fun <reified VM : ViewModel> FragmentActivity.viewModelFactory() =
    viewModels<VM> { dagger.viewModelFactory }

inline fun <reified VM : ViewModel> LifecycleService.viewModelFactory() =
    ViewModelLazy(
        viewModelClass = VM::class,
        storeProducer = { LifecycleViewModelStoreProvider(lifecycle).viewModelStore },
        factoryProducer = { dagger.viewModelFactory }
    )

val Dagger.viewModelFactory get() = appComponent.viewModelFactory()

@Singleton
class AppViewModelFactory @Inject constructor(creators: ViewModelCreators) : ViewModelFactory(creators)

@Module
abstract class AppViewModelModule {
    @Binds
    abstract fun bindAppViewModelFactory(factory: AppViewModelFactory): ViewModelProvider.Factory

    @Binds @IntoMap @ViewModelKey(ControlViewModel::class)
    abstract fun bindControlViewModel(viewModel: ControlViewModel): ViewModel

    @Binds @IntoMap @ViewModelKey(HostViewModel::class)
    abstract fun bindPersonHostViewModel(viewModel: HostViewModel): ViewModel

    @Binds @IntoMap @ViewModelKey(NsdScanViewModel::class)
    abstract fun bindNsdScanViewModel(viewModel: NsdScanViewModel): ViewModel

    @Binds @IntoMap @ViewModelKey(ServerViewModel::class)
    abstract fun bindServerViewModel(viewModel: ServerViewModel): ViewModel

    @Binds @IntoMap @ViewModelKey(ClientViewModel::class)
    abstract fun bindClientViewModel(viewModel: ClientViewModel): ViewModel
}