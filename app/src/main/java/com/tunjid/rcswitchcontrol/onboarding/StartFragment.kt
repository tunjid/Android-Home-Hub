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

import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnAttach
import com.tunjid.androidx.recyclerview.viewbinding.viewHolderFrom
import com.tunjid.globalui.UiState
import com.tunjid.globalui.uiState
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.databinding.FragmentStartBinding
import com.tunjid.rcswitchcontrol.di.dagger
import com.tunjid.rcswitchcontrol.di.nav
import com.tunjid.rcswitchcontrol.navigation.Named
import com.tunjid.rcswitchcontrol.navigation.Node
import com.tunjid.rcswitchcontrol.navigation.updatePartial
import com.tunjid.rcswitchcontrol.server.ServerNsdService
import com.tunjid.rcswitchcontrol.ui.hostscan.HostScan
import kotlinx.parcelize.Parcelize

fun ViewGroup.startScreen() =
    viewHolderFrom(FragmentStartBinding::inflate).apply {
        val dagger = binding.root.context.dagger

        binding.root.doOnAttach {
            it.uiState = UiState(
                toolbarShows = true,
                toolbarTitle = it.context.getString(R.string.app_name)
            )
        }

        val onClick: (View) -> Unit = { v: View ->
            when (v.id) {
                R.id.server -> {
                    ServerNsdService.isServer = true
//                    dagger::nav.updatePartial { push(Node(ClientLoad.StartServer)) }
                }
                R.id.client -> dagger::nav.updatePartial { push(Node(HostScan)) }
            }
        }

        binding.server.setOnClickListener(onClick)
        binding.client.setOnClickListener(onClick)
    }
