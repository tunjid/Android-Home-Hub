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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.fragment.app.Fragment
import com.tunjid.globalui.UiState
import com.tunjid.rcswitchcontrol.App
import com.tunjid.mutator.Mutation
import com.tunjid.mutator.StateHolder
import com.tunjid.mutator.scopedStateHolder
import com.tunjid.rcswitchcontrol.navigation.StackNav
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

val Context.dagger: Dagger get() = (applicationContext as App).dagger
val Service.dagger: Dagger get() = (application as App).dagger
val Activity.dagger: Dagger get() = (application as App).dagger
val Fragment.dagger: Dagger get() = requireActivity().dagger

var Dagger.nav
    get() = appComponent.navStateHolder.state.value
    set(value) = appComponent.navStateHolder.accept(Mutation { value })

data class Dagger(
    val appComponent: AppComponent
) {

    companion object {
        fun make(app: App): Dagger = DaggerAppComponent.builder()
            .appModule(AppModule(app))
            .build()
            .let(::Dagger)
    }
}

val DummyDagger = object : AppComponent {
    override val state: StateFlow<AppState> = MutableStateFlow(AppState())

    override val uiStateHolder: StateHolder<Mutation<UiState>, UiState> = scopedStateHolder(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        initialState = UiState(),
        transform = { emptyFlow() }
    )
    override val navStateHolder: StateHolder<Mutation<StackNav>, StackNav> = scopedStateHolder(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        initialState = StackNav(AppRoot),
        transform = { emptyFlow() }
    )

    override fun broadcasts(): AppBroadcasts = flowOf()

    override val broadcaster: AppBroadcaster = {}

    override fun stateMachineCreator(): StateHolderCreator {
        TODO("Not yet implemented")
    }

    override fun uiScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

}

val AppDependencies = staticCompositionLocalOf { DummyDagger }
