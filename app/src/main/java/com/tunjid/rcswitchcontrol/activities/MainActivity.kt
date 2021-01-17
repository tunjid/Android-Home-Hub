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

package com.tunjid.rcswitchcontrol.activities

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.tunjid.androidx.core.components.services.HardServiceConnection
import com.tunjid.androidx.navigation.Navigator
import com.tunjid.globalui.GlobalUiDriver
import com.tunjid.globalui.GlobalUiHost
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.databinding.ActivityMainBinding
import com.tunjid.rcswitchcontrol.di.dagger
import com.tunjid.rcswitchcontrol.control.ControlFragment
import com.tunjid.rcswitchcontrol.fragments.LandscapeControlFragment
import com.tunjid.rcswitchcontrol.fragments.StartFragment
import com.tunjid.rcswitchcontrol.models.Broadcast
import com.tunjid.rcswitchcontrol.navigation.AppNavigator
import com.tunjid.rcswitchcontrol.client.ClientNsdService
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

//        uiState = if (savedInstanceState == null) UiState.freshState() else savedInstanceState.getParcelable(UI_STATE)!!

        val startIntent = intent

        val isSavedInstance = savedInstanceState != null
        val isNsdServer = ServerNsdService.isServer
        val isNsdClient = startIntent.hasExtra(ClientNsdService.NSD_SERVICE_INFO_KEY) || ClientNsdService.lastConnectedService != null

        if (isNsdServer) HardServiceConnection(applicationContext, ServerNsdService::class.java).start()
        if (isNsdClient) dagger.appComponent.broadcaster(Broadcast.ClientNsd.StartDiscovery())

        if (!isSavedInstance) navigator.push(when {
            App.isAndroidThings || isNsdClient || isNsdServer ->
                if (App.isLandscape) LandscapeControlFragment.newInstance()
                else ControlFragment.newInstance()
            else -> StartFragment.newInstance()
        })
    }
}
