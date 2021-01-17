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
import androidx.lifecycle.ViewModel
import com.tunjid.androidx.core.components.services.HardServiceConnection
import com.tunjid.rcswitchcontrol.di.AppBroadcaster
import com.tunjid.rcswitchcontrol.di.AppContext
import com.tunjid.rcswitchcontrol.models.Broadcast
import com.tunjid.rcswitchcontrol.client.ClientNsdService
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class HostViewModel @Inject constructor(
    @AppContext context: Context,
    private val broadcaster: AppBroadcaster
) : ViewModel() {

    private val disposable: CompositeDisposable = CompositeDisposable()

    private val serverConnection = HardServiceConnection(context, ServerNsdService::class.java)

    init {
        serverConnection.bind()
    }

    override fun onCleared() {
        super.onCleared()
        disposable.clear()
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
