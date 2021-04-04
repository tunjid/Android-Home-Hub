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

import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.tunjid.androidx.navigation.Navigator
import com.tunjid.globalui.GlobalUiDriver
import com.tunjid.globalui.GlobalUiHost
import com.tunjid.rcswitchcontrol.client.ClientNsdService
import com.tunjid.rcswitchcontrol.control.ControlFragment
import com.tunjid.rcswitchcontrol.client.ClientLoad
import com.tunjid.rcswitchcontrol.control.LandscapeControlFragment
import com.tunjid.rcswitchcontrol.databinding.ActivityMainBinding
import com.tunjid.rcswitchcontrol.navigation.AppNavigator
import com.tunjid.rcswitchcontrol.onboarding.StartFragment
import com.tunjid.rcswitchcontrol.server.ServerNsdService

class MainActivity : AppCompatActivity(),
    GlobalUiHost,
    Navigator.Controller {

    override val navigator: AppNavigator by lazy { AppNavigator(this) }
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    override val globalUiController by lazy { GlobalUiDriver(this, binding, navigator) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        window.statusBarColor = ContextCompat.getColor(this, R.color.transparent)
        if (ServerNsdService.isServer) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val startIntent = intent

        val isSavedInstance = savedInstanceState != null
        val controlLoad = when (ServerNsdService.isServer || App.isAndroidThings) {
            true -> ClientLoad.StartServer
            false -> when (startIntent.hasExtra(ClientNsdService.NSD_SERVICE_INFO_KEY)) {
                true -> startIntent.getParcelableExtra<NsdServiceInfo>(ClientNsdService.NSD_SERVICE_INFO_KEY)
                    ?.let(ClientLoad::NewClient)
                false -> ClientNsdService.lastConnectedService
                    ?.let(ClientLoad::ExistingClient)
            }
        }

        if (!isSavedInstance) navigator.push(when (controlLoad) {
            null -> StartFragment.newInstance()
            else -> when(App.isLandscape ) {
                true -> LandscapeControlFragment.newInstance(controlLoad)
                false -> ControlFragment.newInstance(controlLoad)
            }
        })
    }
}
