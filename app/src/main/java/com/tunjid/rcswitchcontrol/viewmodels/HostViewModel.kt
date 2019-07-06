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

package com.tunjid.rcswitchcontrol.viewmodels

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.tunjid.androidbootstrap.core.components.ServiceConnection
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster
import com.tunjid.rcswitchcontrol.services.ClientNsdService
import com.tunjid.rcswitchcontrol.services.ServerNsdService
import io.reactivex.disposables.CompositeDisposable

class HostViewModel(application: Application) : AndroidViewModel(application) {

    private val disposable: CompositeDisposable = CompositeDisposable()

    private val serverConnection: ServiceConnection<ServerNsdService> = ServiceConnection(ServerNsdService::class.java)

    override fun onCleared() {
        super.onCleared()
        disposable.clear()
        serverConnection.unbindService()
    }

    fun restartServer() {
        if (serverConnection.isBound) serverConnection.boundService.restart()
    }

    fun stop() {
        ServerNsdService.isServer = false
        ClientNsdService.lastConnectedService = null
        Broadcaster.push(Intent(ServerNsdService.ACTION_STOP))
    }

    fun nameServer(name: String) {
        ServerNsdService.isServer = true
        ServerNsdService.serviceName = name

        val context = getApplication<Application>()
        serverConnection.with(context).start()
        serverConnection.with(context).bind()
    }
}