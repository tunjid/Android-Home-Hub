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

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.ViewGroup
import androidx.core.view.doOnAttach
import com.tunjid.androidx.recyclerview.gridLayoutManager
import com.tunjid.androidx.recyclerview.listAdapterOf
import com.tunjid.androidx.recyclerview.viewbinding.BindingViewHolder
import com.tunjid.androidx.recyclerview.viewbinding.viewHolderDelegate
import com.tunjid.androidx.recyclerview.viewbinding.viewHolderFrom
import com.tunjid.globalui.uiState
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.common.mapDistinct
import com.tunjid.rcswitchcontrol.control.attachedScope
import com.tunjid.rcswitchcontrol.databinding.FragmentListBinding
import com.tunjid.rcswitchcontrol.databinding.ViewholderHostCardBinding
import com.tunjid.rcswitchcontrol.di.dagger
import com.tunjid.rcswitchcontrol.di.nav
import com.tunjid.rcswitchcontrol.di.stateMachine
import com.tunjid.rcswitchcontrol.navigation.Named
import com.tunjid.rcswitchcontrol.navigation.Node
import com.tunjid.rcswitchcontrol.navigation.updatePartial
import com.tunjid.rcswitchcontrol.onboarding.Start
import com.tunjid.rcswitchcontrol.utils.makeAccessibleForTV
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

@Parcelize
object Host : Named

fun ViewGroup.hostScreen(node: Node): BindingViewHolder<FragmentListBinding> =
    viewHolderFrom(FragmentListBinding::inflate).apply {
        val scope = binding.attachedScope()
        val dagger = binding.root.context.dagger
        val stateMachine = dagger.stateMachine<HostViewModel>(node)

        binding.list.apply {
            val listAdapter = listAdapterOf(
                initialItems = stateMachine.state.value.let(binding.root.context::items),
                viewHolderCreator = { parent, _ ->
                    parent.viewHolderFrom(ViewholderHostCardBinding::inflate).apply {
                        binding.button.strokeColor = ColorStateList.valueOf(Color.WHITE)
                        binding.button.makeAccessibleForTV(stroked = true)
                        binding.root.setOnClickListener {
                            when (item.id) {
//                            R.string.rename_server -> NameServiceDialogFragment.newInstance()
//                                .show(childFragmentManager, "")
                                R.string.restart_server -> stateMachine.restartServer()
                                R.string.stop_server -> {
                                    stateMachine.stop()
                                    dagger::nav.updatePartial { filter { it.named is Start } }
                                }
                                else -> Unit
                            }
                        }
                    }
                },
                viewHolderBinder = { holder, item, _ ->
                    holder.item = item
                    holder.binding.button.text = item.text
                }
            )

            layoutManager = gridLayoutManager(2)
            adapter = listAdapter

            binding.root.doOnAttach {
                binding::uiState.updatePartial { copy(altToolbarShows = false) }

                scope.launch {
                    stateMachine.state
                        .mapDistinct(binding.root.context::items)
                        .collect(listAdapter::submitList)
                }
            }
        }

//    override fun onServiceNamed(name: String) = stateMachine.nameServer(name)

    }

private var BindingViewHolder<ViewholderHostCardBinding>.item by viewHolderDelegate<HostItem>()