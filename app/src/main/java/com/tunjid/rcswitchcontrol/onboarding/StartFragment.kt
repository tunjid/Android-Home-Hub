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

package com.tunjid.rcswitchcontrol.onboarding

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.tunjid.androidx.navigation.activityNavigatorController
import com.tunjid.globalui.UiState
import com.tunjid.globalui.uiState
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.control.ControlFragment
import com.tunjid.rcswitchcontrol.control.ControlLoad
import com.tunjid.rcswitchcontrol.databinding.FragmentStartBinding
import com.tunjid.rcswitchcontrol.navigation.AppNavigator
import com.tunjid.rcswitchcontrol.server.ServerNsdService

class StartFragment : Fragment(R.layout.fragment_start) {

    private val navigator by activityNavigatorController<AppNavigator>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        uiState = UiState(
                toolbarShows = true,
                toolbarTitle = getString(R.string.app_name)
        )

        val onClick: (View) -> Unit = { v: View ->
            when (v.id) {
                R.id.server -> {
                    ServerNsdService.isServer = true
                    navigator.push(ControlFragment.newInstance(ControlLoad.StartServer))
                }
                R.id.client -> navigator.push(HostScanFragment.newInstance())
            }
        }

        FragmentStartBinding.bind(view).apply {
            server.setOnClickListener(onClick)
            client.setOnClickListener(onClick)
        }
    }

    companion object {

        fun newInstance(): StartFragment {
            val startFragment = StartFragment()
            val args = Bundle()
            startFragment.arguments = args
            return startFragment
        }
    }
}
