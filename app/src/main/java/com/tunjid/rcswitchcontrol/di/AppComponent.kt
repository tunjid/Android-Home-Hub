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

import com.tunjid.globalui.UiState
import com.tunjid.rcswitchcontrol.common.ClosableStateMachine
import com.tunjid.rcswitchcontrol.common.StateMachine
import com.tunjid.rcswitchcontrol.common.Mutation
import com.tunjid.rcswitchcontrol.navigation.Node
import com.tunjid.rcswitchcontrol.navigation.StackNav
import dagger.Component
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Singleton
import kotlin.reflect.KMutableProperty0

@Singleton
@Component(modules = [
    AppModule::class,
    AppViewModelModule::class,
])
interface AppComponent {

    val state: StateFlow<AppState>

    val uiStateMachine: StateMachine<Mutation<UiState>, UiState>

    val navStateMachine: StateMachine<Mutation<StackNav>, StackNav>

    fun broadcasts(): AppBroadcasts

    val broadcaster: AppBroadcaster

    fun stateMachineCreator(): StateMachineCreator

    @UiScope
    fun uiScope() : CoroutineScope
}

inline fun <reified SM : ClosableStateMachine<*, *>> AppComponent.stateMachine(
    node: Node? = null
) = stateMachineCreator().invoke(node, SM::class) as SM