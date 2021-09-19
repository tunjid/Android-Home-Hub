/*
 * MIT License
 *
 * Copyright (c) 2019 Adetunji Dahunsi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tunjid.rcswitchcontrol.server

import android.content.Context
import com.tunjid.androidx.core.components.services.HardServiceConnection
import com.tunjid.rcswitchcontrol.common.ClosableStateHolder
import com.tunjid.rcswitchcontrol.client.ClientNsdService
import com.tunjid.rcswitchcontrol.di.AppBroadcaster
import com.tunjid.rcswitchcontrol.di.AppContext
import com.tunjid.rcswitchcontrol.di.UiScope
import com.tunjid.rcswitchcontrol.models.Broadcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

class HostViewModel @Inject constructor(
    @UiScope scope: CoroutineScope,
    @AppContext context: Context,
    private val broadcaster: AppBroadcaster
) : ClosableStateHolder<Unit, State>(scope) {

    private val proxyState = MutableStateFlow<Flow<State>>(emptyFlow())
    private val serverConnection =
        HardServiceConnection(context, ServerNsdService::class.java) { server ->
            proxyState.value = server.state
        }

    override val state: StateFlow<State> = proxyState
        .flatMapLatest { it }
        .stateIn(
            scope = scope,
            initialValue = State(),
            started = SharingStarted.WhileSubscribed(),
        )

    override val accept: (Unit) -> Unit = { }

    init {
        serverConnection.bind()
    }

    override fun close() {
        super.close()
        proxyState.value = emptyFlow()
        serverConnection.unbindService()
    }

    fun restartServer() {
        serverConnection.boundService?.restart()
    }

    fun stop() {
        ServerNsdService.isServer = false
        ClientNsdService.lastConnectedService = null
        broadcaster(Broadcast.ServerNsd.Stop)
    }

    fun nameServer(name: String) {
        ServerNsdService.isServer = true
        ServerNsdService.serviceName = name

        serverConnection.start()
        serverConnection.bind()
    }
}
