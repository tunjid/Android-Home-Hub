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

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.models.Broadcast
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Qualifier
import javax.inject.Singleton

typealias AppBroadcaster = (@JvmSuppressWildcards Broadcast) -> Unit
typealias AppBroadcasts = Flow<Broadcast>
typealias AppScope = CoroutineScope

@Qualifier
annotation class AppContext

@Module
class AppModule(private val app: App) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val broadcaster = MutableSharedFlow<Broadcast>(
        replay = 1,
        extraBufferCapacity = 5,
    )

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) appScope.cancel()
        })
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
}