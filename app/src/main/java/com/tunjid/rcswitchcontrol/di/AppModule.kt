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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.arch.ClosableStateMachine
import com.tunjid.rcswitchcontrol.common.Mutation
import com.tunjid.rcswitchcontrol.common.mapDistinct
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty0

private typealias StateMachineCache = MutableMap<String, MutableMap<KClass<out ClosableStateMachine<*, *>>, ClosableStateMachine<*, *>>>
typealias StateMachineCreator = (node: Node?, modelClass: KClass<out ClosableStateMachine<*, *>>) -> ClosableStateMachine<*, *>

typealias AppBroadcaster = (@JvmSuppressWildcards Broadcast) -> Unit
typealias AppBroadcasts = Flow<Broadcast>
typealias AppScope = CoroutineScope

@Qualifier
annotation class AppContext

@Qualifier
annotation class UiScope

enum class AppStatus {
    Resumed, Paused, Stopped, Destroyed
}

@Parcelize
data class SimpleName(override val name: String) : Named

val AppRoot = Node(SimpleName("Root"))

data class AppState(
    val nav: StackNav = StackNav(
        root = AppRoot,
    ).push(Node(Start)),
    val status: AppStatus = AppStatus.Destroyed
)

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

    private val appStateFlow: StateFlow<AppState>
    private val appStateMutations = MutableSharedFlow<Mutation<AppState>>()


    private var nav
        get() = appStateFlow.value.nav
        set(value) {
            if (appScope.isActive) appScope.launch {
                appStateMutations.emit(Mutation { copy(nav = value) })
            }
        }

    init {
        val appStatuses = callbackFlow<Mutation<AppState>> {
            val callback = object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    when (val restoredNav = savedInstanceState?.get(NAV)) {
                        is StackNav -> nav = restoredNav
                    }
                }

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) {
                    channel.trySend(Mutation { copy(status = AppStatus.Resumed) })
                }

                override fun onActivityPaused(activity: Activity) {
                    channel.trySend(Mutation { copy(status = AppStatus.Paused) })
                }

                override fun onActivityStopped(activity: Activity) {
                    channel.trySend(Mutation { copy(status = AppStatus.Stopped) })
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                    outState.putParcelable(NAV, nav)
                }

                override fun onActivityDestroyed(activity: Activity) {
                    channel.trySend(Mutation { copy(status = AppStatus.Destroyed) })
                }
            }
            app.registerActivityLifecycleCallbacks(callback)
            awaitClose { app.unregisterActivityLifecycleCallbacks(callback) }
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) appScope.cancel()
        })

        appStateFlow = merge(
            appStatuses,
            appStateMutations
        )
            .scan(AppState()) { state, mutations -> mutations.change(state) }
            .stateIn(
                scope = appScope,
                started = SharingStarted.Eagerly,
                initialValue = AppState()
            )

        appScope.launch {
            appStateFlow
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
    fun provideAppScope(): AppScope = appScope

    @Provides
    @Singleton
    fun provideStateMachineCreator(factory: StateMachineFactory): StateMachineCreator =
        { node, modelClass ->
            val creator = factory[modelClass.java]
                ?: factory.entries.firstOrNull { modelClass.java.isAssignableFrom(it.key) }?.value
                ?: throw IllegalArgumentException("unknown model class $modelClass")

            when(node) {
                null ->  creator.get()
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
    fun provideNav(): KMutableProperty0<StackNav> = ::nav

    @Provides
    @Singleton
    fun provideAppStateFlow(): StateFlow<AppState> = appStateFlow

    @Provides
    @UiScope
    fun provideUiScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}