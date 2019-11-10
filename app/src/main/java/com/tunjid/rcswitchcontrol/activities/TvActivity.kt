/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.tunjid.rcswitchcontrol.activities

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.tunjid.androidx.core.components.services.HardServiceConnection
import com.tunjid.androidx.navigation.Navigator
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.fragments.ControlFragment
import com.tunjid.rcswitchcontrol.services.ServerNsdService
import com.tunjid.rcswitchcontrol.utils.AppNavigator
import com.tunjid.rcswitchcontrol.utils.GlobalUiController
import com.tunjid.rcswitchcontrol.utils.globalUiDriver

class TvActivity : FragmentActivity(R.layout.activity_main),
        GlobalUiController,
        Navigator.Controller{

    override val navigator: AppNavigator by lazy { AppNavigator(this) }

    override var uiState by globalUiDriver(currentSource = navigator::current)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_tv)

        HardServiceConnection(applicationContext, ServerNsdService::class.java).start()

        if (savedInstanceState == null) navigator.push(ControlFragment.newInstance())
    }
}
