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

package com.tunjid.rcswitchcontrol

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tunjid.rcswitchcontrol.client.ClientLoad
import com.tunjid.rcswitchcontrol.client.ClientNsdService
import com.tunjid.rcswitchcontrol.client.nsdServiceInfo
import com.tunjid.rcswitchcontrol.client.nsdServiceName
import com.tunjid.rcswitchcontrol.di.AppDependencies
import com.tunjid.rcswitchcontrol.di.dagger
import com.tunjid.rcswitchcontrol.di.nav
import com.tunjid.rcswitchcontrol.navigation.Node
import com.tunjid.rcswitchcontrol.navigation.updatePartial
import com.tunjid.rcswitchcontrol.server.ServerNsdService
import com.tunjid.rcswitchcontrol.ui.root.Root
import com.tunjid.rcswitchcontrol.ui.root.insetMutations
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(){


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this) {
            dagger::nav.updatePartial { pop() }
        }

        setContent {
            CompositionLocalProvider(AppDependencies provides dagger.appComponent) {
                Root()
            }
        }

        window.statusBarColor = ContextCompat.getColor(this, R.color.transparent)
        if (ServerNsdService.isServer) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val startIntent = intent

        val controlLoad = when (ServerNsdService.isServer || App.isAndroidThings) {
            true -> ClientLoad.StartServer
            false -> when (val info = startIntent.nsdServiceInfo) {
                null -> (startIntent.nsdServiceName ?: ClientNsdService.lastConnectedService)
                    ?.let(ClientLoad::ExistingClient)
                else -> info.let(ClientLoad::NewClient)
            }
        }

        if (controlLoad != null) dagger::nav.updatePartial { push(Node(controlLoad)) }

        lifecycleScope.launch {
            insetMutations().collect {
                dagger.appComponent.uiStateMachine.accept(it)
            }
        }
    }
}
