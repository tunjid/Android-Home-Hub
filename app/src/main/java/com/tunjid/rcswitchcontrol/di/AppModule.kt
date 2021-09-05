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
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.tunjid.globalui.UiState
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.common.ClosableStateMachine
import com.tunjid.rcswitchcontrol.common.Mutation
import com.tunjid.rcswitchcontrol.common.StateMachine
import com.tunjid.rcswitchcontrol.common.derived
import com.tunjid.rcswitchcontrol.common.mapDistinct
import com.tunjid.rcswitchcontrol.common.stateMachineOf
import com.tunjid.rcswitchcontrol.models.Broadcast
import com.tunjid.rcswitchcontrol.navigation.Named
import com.tunjid.rcswitchcontrol.navigation.Node
import com.tunjid.rcswitchcontrol.navigation.StackNav
import com.tunjid.rcswitchcontrol.onboarding.Start
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.reflect.KClass

private typealias StateMachineCache = MutableMap<String, MutableMap<KClass<out ClosableStateMachine<*, *>>, ClosableStateMachine<*, *>>>
typealias StateMachineCreator = (node: Node?, modelClass: KClass<out ClosableStateMachine<*, *>>) -> ClosableStateMachine<*, *>

typealias AppBroadcaster = (@JvmSuppressWildcards Broadcast) -> Unit
typealias AppBroadcasts = Flow<Broadcast>

@Qualifier
annotation class AppContext

@Qualifier
annotation class UiScope

enum class AppStatus {
    Resumed, Paused, Stopped, Destroyed
}

data class AppState(
    val ui: UiState = UiState(),
    val nav: StackNav = StackNav(
        root = AppRoot,
    ).push(Node(Start)),
    val status: AppStatus = AppStatus.Destroyed
)

@Parcelize
data class SimpleName(override val name: String) : Named

val AppRoot = Node(SimpleName("Root"))

val AppState.isResumed get() = status == AppStatus.Resumed

private const val NAV = "nav"

@Module
class AppModule(private val app: App) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val broadcaster = MutableSharedFlow<Broadcast>(
        replay = 1,
        extraBufferCapacity = 5,
    )

    private val stateMachineCache: StateMachineCache = mutableMapOf()

    private val appStateMachine = stateMachineOf(
        scope = appScope,
        started = SharingStarted.Eagerly,
        initialState = AppState(),
        transform = { mutations: Flow<Mutation<AppState>> -> mutations }
    )

    private val uiStateMachine = appStateMachine.derived(
        scope = appScope,
        mapper = AppState::ui,
        mutator = { appState, ui -> appState.copy(ui = ui) }
    )

    private val navStateMachine = appStateMachine.derived(
        scope = appScope,
        mapper = AppState::nav,
        mutator = { appState, nav -> appState.copy(nav = nav) }
    )

    private val appLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            when (val restoredNav = savedInstanceState?.get(NAV)) {
                is StackNav -> appStateMachine.accept(Mutation { copy(nav = restoredNav) })
            }
        }

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityResumed(activity: Activity) {
            appStateMachine.accept(Mutation { copy(status = AppStatus.Resumed) })
        }

        override fun onActivityPaused(activity: Activity) {
            appStateMachine.accept(Mutation { copy(status = AppStatus.Paused) })
        }

        override fun onActivityStopped(activity: Activity) {
            appStateMachine.accept(Mutation { copy(status = AppStatus.Stopped) })
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            outState.putParcelable(NAV, appStateMachine.state.value.nav)
        }

        override fun onActivityDestroyed(activity: Activity) {
            appStateMachine.accept(Mutation { copy(status = AppStatus.Destroyed) })
        }
    }

    init {
        app.registerActivityLifecycleCallbacks(appLifecycleCallbacks)
        appScope.launch {
            appStateMachine.state
                .mapDistinct { it.nav }
                .onEach { println(it.allNodes.map { it.named }) }
                .scan(listOf<StackNav>()) { list, nav -> list.plus(element = nav).takeLast(2) }
                .filter { it.size > 1 }
                .map { (old, new) ->
                    old.allNodes.map(Node::path) - new.allNodes.map(Node::path)
                }
                .collect { removedPaths: List<String> ->
                    removedPaths.forEach {
                        val stateMachineMap = stateMachineCache.remove(it)
                        stateMachineMap?.values?.forEach(ClosableStateMachine<*, *>::close)
                        println("Removing all SM for $it")
                    }
                }
        }
        appScope.launch {
            appStateMachine.state
                .mapDistinct { it.ui.toolbarTitle }
                .collect { println("") }
        }
    }

    @Provides
    @Singleton
    fun provideDagger(): Dagger = app.dagger

    @Provides
    @AppContext
    fun provideAppContext(): Context = app

    @Provides
    @Singleton
    fun provideAppBroadcasts(): AppBroadcasts = broadcaster

    @Provides
    @Singleton
    fun provideAppBroadcaster(): AppBroadcaster = { broadcaster.tryEmit(it) }

    @Provides
    @Singleton
    fun provideStateMachineCreator(factory: StateMachineFactory): StateMachineCreator =
        { node, modelClass ->
            val creator = factory[modelClass.java]
                ?: factory.entries.firstOrNull { modelClass.java.isAssignableFrom(it.key) }?.value
                ?: throw IllegalArgumentException("unknown model class $modelClass")

            when (node) {
                null -> creator.get()
                else -> {
                    val classToInstances = stateMachineCache.getOrPut(node.path) { mutableMapOf() }
                    classToInstances.getOrPut(modelClass) {
                        try {
                            creator.get()
                        } catch (e: Exception) {
                            throw RuntimeException(e)
                        }
                    }
                }
            }
        }

    @Provides
    @Singleton
    fun provideUiStateMachine(): StateMachine<Mutation<UiState>, UiState> = uiStateMachine

    @Provides
    @Singleton
    fun provideNavStateMachine(): StateMachine<Mutation<StackNav>, StackNav> = navStateMachine

    @Provides
    @Singleton
    fun provideAppStateFlow(): StateFlow<AppState> = appStateMachine.state

    @Provides
    @UiScope
    fun provideUiScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}