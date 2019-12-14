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

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.tunjid.androidx.core.components.services.HardServiceConnection
import com.tunjid.androidx.navigation.Navigator
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.common.Broadcaster
import com.tunjid.rcswitchcontrol.fragments.ControlFragment
import com.tunjid.rcswitchcontrol.fragments.StartFragment
import com.tunjid.rcswitchcontrol.fragments.LandscapeControlFragment
import com.tunjid.rcswitchcontrol.navigation.AppNavigator
import com.tunjid.rcswitchcontrol.services.ClientNsdService
import com.tunjid.rcswitchcontrol.services.ServerNsdService
import com.tunjid.rcswitchcontrol.utils.GlobalUiController
import com.tunjid.rcswitchcontrol.utils.WindowInsetsDriver
import com.tunjid.rcswitchcontrol.utils.globalUiDriver

class MainActivity : AppCompatActivity(R.layout.activity_main),
        GlobalUiController,
        Navigator.Controller {

    override val navigator: AppNavigator by lazy { AppNavigator(this) }

    override var uiState by globalUiDriver(currentSource = navigator::current)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = ContextCompat.getColor(this, R.color.transparent)
        supportFragmentManager.registerFragmentLifecycleCallbacks(windowInsetsDriver(), false)
        supportFragmentManager.registerFragmentLifecycleCallbacks(transientBarCallback(), false)

        uiState = uiState.copy(fabShows = false)
//        uiState = if (savedInstanceState == null) UiState.freshState() else savedInstanceState.getParcelable(UI_STATE)!!

        val startIntent = intent

        val isSavedInstance = savedInstanceState != null
        val isNsdServer = ServerNsdService.isServer
        val isNsdClient = startIntent.hasExtra(ClientNsdService.NSD_SERVICE_INFO_KEY) || ClientNsdService.lastConnectedService != null

        if (isNsdServer) HardServiceConnection(applicationContext, ServerNsdService::class.java).start()
        if (isNsdClient) Broadcaster.push(Intent(ClientNsdService.ACTION_START_NSD_DISCOVERY))

        if (!isSavedInstance) navigator.push(when {
            App.isAndroidThings || isNsdClient || isNsdServer ->
                if (App.isLandscape) LandscapeControlFragment.newInstance()
                else ControlFragment.newInstance()
            else -> StartFragment.newInstance()
        })
    }

    private fun adjustKeyboardPadding(suggestion: Int): Int = suggestion

    private fun windowInsetsDriver(): WindowInsetsDriver = WindowInsetsDriver(
            stackNavigatorSource = this.navigator::activeNavigator,
            parentContainer = findViewById(R.id.constraint_layout),
            contentContainer = findViewById(R.id.main_fragment_container),
            coordinatorLayout = findViewById(R.id.coordinator_layout),
            toolbar = findViewById(R.id.toolbar),
            topInsetView = findViewById(R.id.top_inset),
            bottomInsetView = findViewById(R.id.bottom_inset),
            keyboardPadding = findViewById(R.id.keyboard_padding),
            insetAdjuster = this::adjustKeyboardPadding
    )

    private fun transientBarCallback() = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
            if (f.id == navigator.activeNavigator.containerId) navigator.transientBarDriver.clearTransientBars()
        }
    }
}
