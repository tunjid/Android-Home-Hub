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


import android.net.nsd.NsdServiceInfo
import android.text.SpannableStringBuilder
import android.view.ViewGroup
import androidx.core.view.doOnAttach
import com.tunjid.androidx.core.text.scale
import com.tunjid.androidx.recyclerview.listAdapterOf
import com.tunjid.androidx.recyclerview.verticalLayoutManager
import com.tunjid.androidx.recyclerview.viewbinding.BindingViewHolder
import com.tunjid.androidx.recyclerview.viewbinding.viewHolderDelegate
import com.tunjid.androidx.recyclerview.viewbinding.viewHolderFrom
import com.tunjid.globalui.UiState
import com.tunjid.globalui.uiState
import com.tunjid.globalui.updatePartial
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.common.asSuspend
import com.tunjid.rcswitchcontrol.common.mapDistinct
import com.tunjid.rcswitchcontrol.control.attachedScope
import com.tunjid.rcswitchcontrol.control.whileAttached
import com.tunjid.rcswitchcontrol.databinding.FragmentNsdScanBinding
import com.tunjid.rcswitchcontrol.databinding.ViewholderNsdListBinding
import com.tunjid.rcswitchcontrol.di.dagger
import com.tunjid.rcswitchcontrol.di.isResumed
import com.tunjid.rcswitchcontrol.di.stateMachine
import com.tunjid.rcswitchcontrol.navigation.Node
import com.tunjid.rcswitchcontrol.ui.hostscan.Input
import com.tunjid.rcswitchcontrol.ui.hostscan.NSDState
import com.tunjid.rcswitchcontrol.ui.hostscan.NsdItem
import com.tunjid.rcswitchcontrol.ui.hostscan.HostScanStateHolder
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * A [androidx.fragment.app.Fragment] listing supported NSD servers
 */

fun ViewGroup.hostScanScreen(node: Node) =
    viewHolderFrom(FragmentNsdScanBinding::inflate).apply {
        val scope = this.binding.attachedScope()
        val dagger = this.binding.root.context.dagger
        val stateMachine = dagger.stateMachine<HostScanStateHolder>(node)

        val scanDevices = { enable: Boolean ->
            stateMachine.accept(
                if (enable) Input.StartScanning
                else Input.StopScanning
            )
        }


        val onServiceClicked = { serviceInfo: NsdServiceInfo ->
//            dagger::nav.updatePartial { push(Node(ClientLoad.NewClient(serviceInfo))) }
        }

        binding.list.apply {
            val listAdapter = listAdapterOf(
                initialItems = stateMachine.state.value.items,
                viewHolderCreator = { parent, _ ->
                    parent.viewHolderFrom(ViewholderNsdListBinding::inflate).apply {
                        this.binding.title.setOnClickListener { onServiceClicked(this.item.info) }
                    }
                },
                viewHolderBinder = { holder, service, _ -> holder.bind(service) }
            )

            this.layoutManager = this.verticalLayoutManager()
            this.adapter = listAdapter

            binding.root.doOnAttach {
                this.uiState = UiState(
                    toolbarShows = true,
                    toolbarTitle = binding.root.context.getString(R.string.app_name),
//                    toolbarMenuRes = R.menu.menu_nsd_scan,
//                    toolbarMenuClickListener = binding.whileAttached { item: MenuItem ->
//                        when (item.itemId) {
//                            R.id.menu_scan -> scanDevices(true)
//                            R.id.menu_stop -> scanDevices(false)
//                            else -> Unit
//                        }
//                    },
                )

                ::uiState.updatePartial {
                    copy(toolbarMenuRefresher = binding.whileAttached { menu ->
                        scope.launch {
                            stateMachine.state
                                .mapDistinct(NSDState::isScanning.asSuspend)
                                .collect { isScanning ->

                                    menu.findItem(R.id.menu_stop)?.isVisible = isScanning
                                    menu.findItem(R.id.menu_scan)?.isVisible = !isScanning

                                    val refresh = menu.findItem(R.id.menu_refresh)

                                    refresh?.isVisible = isScanning
                                    if (isScanning) refresh?.setActionView(R.layout.actionbar_indeterminate_progress)
                                }
                        }
                    })
                }

                scope.launch {
                    stateMachine.state
                        .mapDistinct(NSDState::items.asSuspend)
                        .collect(listAdapter::submitList)
                }
                scope.launch {
                    stateMachine.state
                        .mapDistinct(NSDState::isScanning.asSuspend)
                        .collect {
//                            binding::uiState.updatePartial { this.copy(toolbarInvalidated = true) }
                        }
                }
                scope.launch {
                    dagger.appComponent.state
                        .mapDistinct { it.isResumed }
                        .collect { scanDevices(it) }
                }
            }
        }
    }

private var BindingViewHolder<ViewholderNsdListBinding>.item by viewHolderDelegate<NsdItem>()

private fun BindingViewHolder<ViewholderNsdListBinding>.bind(item: NsdItem) {
    this.item = item
    binding.title.text = SpannableStringBuilder()
        .append(item.info.serviceName)
        .append("\n")
        .append(item.info.host.hostAddress.scale(0.8F))
}