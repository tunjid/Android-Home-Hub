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

package com.tunjid.rcswitchcontrol.control

import android.view.ViewGroup
import androidx.core.view.doOnAttach
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.Callback.makeMovementFlags
import androidx.recyclerview.widget.RecyclerView
import com.rcswitchcontrol.zigbee.models.ZigBeeCommand
import com.rcswitchcontrol.zigbee.models.payload
import com.tunjid.androidx.recyclerview.gridLayoutManager
import com.tunjid.androidx.recyclerview.listAdapterOf
import com.tunjid.androidx.recyclerview.setSwipeDragOptions
import com.tunjid.androidx.recyclerview.viewbinding.BindingViewHolder
import com.tunjid.androidx.recyclerview.viewbinding.typed
import com.tunjid.androidx.recyclerview.viewbinding.viewHolderFrom
import com.tunjid.globalui.liveUiState
import com.tunjid.globalui.uiState
import com.tunjid.globalui.updatePartial
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.client.ClientState
import com.tunjid.rcswitchcontrol.common.asSuspend
import com.tunjid.rcswitchcontrol.common.mapDistinct
import com.tunjid.rcswitchcontrol.databinding.FragmentListBinding
import com.tunjid.rcswitchcontrol.databinding.ViewholderPaddingBinding
import com.tunjid.rcswitchcontrol.databinding.ViewholderRemoteSwitchBinding
import com.tunjid.rcswitchcontrol.databinding.ViewholderZigbeeDeviceBinding
import com.tunjid.rcswitchcontrol.di.dagger
import com.tunjid.rcswitchcontrol.di.isResumed
import com.tunjid.rcswitchcontrol.di.stateMachine
import com.tunjid.rcswitchcontrol.navigation.Node
import com.tunjid.rcswitchcontrol.utils.SpanCountCalculator
import com.tunjid.rcswitchcontrol.viewholders.DeviceAdapterListener
import com.tunjid.rcswitchcontrol.viewholders.bind
import com.tunjid.rcswitchcontrol.viewholders.rfDeviceDeviceViewHolder
import com.tunjid.rcswitchcontrol.viewholders.zigbeeDeviceViewHolder
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

fun ViewGroup.devicesScreen(node: Node): BindingViewHolder<FragmentListBinding> =
    viewHolderFrom(FragmentListBinding::inflate).apply {
        val scope = binding.attachedScope()
        val dagger = binding.root.context.dagger
        val stateMachine = dagger.stateMachine<ControlViewModel>(node)

//    addOnBackPressedCallback {
//        isEnabled = stateMachine.state.value.selectedDevices.isEmpty()
//
//        if (!isEnabled) activity?.onBackPressed()
//        else stateMachine.accept(Input.Sync.ClearSelections)
//    }

        val deviceAdapterListener = object : DeviceAdapterListener {
            override fun onClicked(device: Device) {
                if (stateMachine.state.value.selectedDevices.isNotEmpty()) onLongClicked(device)
            }

            override fun onLongClicked(device: Device) {
                stateMachine.accept(Input.Sync.Select(device))
            }

            override fun onSwitchToggled(device: Device, isOn: Boolean) = when (device) {
                is Device.RF -> stateMachine.accept(
                    Input.Async.ServerCommand(
                        device.togglePayload(
                            isOn
                        )
                    )
                )
                else -> Unit
            }

            override fun send(command: ZigBeeCommand) =
                stateMachine.accept(Input.Async.ServerCommand(command.payload))

//        override fun onGroupNamed(groupName: CharSequence) {
//            stateMachine.accept(Input.Sync.ClearSelections)
//        }
        }

        val refreshUi = {
            binding::uiState.updatePartial {
                val selected = stateMachine.state.value.selectedDevices
                copy(
                    altToolbarTitle = binding.root.context.getString(
                        R.string.devices_selected,
                        stateMachine.state.value.selectedDevices.size
                    ),
                    altToolbarShows = !selected.isNullOrEmpty()
                )
            }
        }

        binding.list.apply {
            val listAdapter = listAdapterOf(
                initialItems = stateMachine.state.value.selectedDevices,
                viewHolderCreator = { parent: ViewGroup, viewType: Int ->
                    when (viewType) {
                        Device.RF::class.hashCode() -> parent.rfDeviceDeviceViewHolder(
                            deviceAdapterListener
                        )
                        Device.ZigBee::class.hashCode() -> parent.zigbeeDeviceViewHolder(
                            deviceAdapterListener
                        )
                        else -> parent.viewHolderFrom(ViewholderPaddingBinding::inflate)
                    }
                },
                viewTypeFunction = { device -> device::class.hashCode() },
                viewHolderBinder = { holder, device, _ ->
                    when (device) {
                        is Device.RF -> holder.typed<ViewholderRemoteSwitchBinding>().bind(device)
                        is Device.ZigBee -> holder.typed<ViewholderZigbeeDeviceBinding>()
                            .bind(device)
                    }
                },
                itemIdFunction = { it.hashCode().toLong() }
            )

            adapter = listAdapter
            layoutManager = gridLayoutManager(spanCount = SpanCountCalculator.spanCount)
            itemAnimator = DefaultItemAnimator().apply {
                changeDuration = 0L
                supportsChangeAnimations = false
            }

            setSwipeDragOptions<BindingViewHolder<*>>(
                swipeConsumer = { _: RecyclerView.ViewHolder, _ ->

                },
                movementFlagFunction = { holder ->
                    // TODO fix this
                    val isDeleting = false
                    if (isDeleting || holder.binding is ViewholderZigbeeDeviceBinding) 0
                    else makeMovementFlags(0, ItemTouchHelper.LEFT)
                },
                itemViewSwipeSupplier = { true }
            )

            val clientState = stateMachine.state
                .mapDistinct(ControlState::clientState.asSuspend)
                .mapDistinct(ClientState::devices.asSuspend)

            binding.root.doOnAttach {
//                scope.launch {
//                    binding.liveUiState
//                        .mapDistinct { it.systemUI.dynamic.bottomInset }
//                        .collect {
//                            binding.list.updatePadding(bottom = it)
//                        }
//                }
                scope.launch {
                    clientState
                        .collect {
                            listAdapter.submitList(it)
                            refreshUi()
                        }
                }
                scope.launch {
                    dagger.appComponent.state
                        .mapDistinct { it.isResumed }
                        .collect { refreshUi() }
                }
            }


//            clientState.mapDistinct {
//                it.filterIsInstance<Device.ZigBee>()
//                    .map(Device.ZigBee::trifecta)
//                    .map(Triple<Pair<String, Any?>, Pair<String, Any?>, Pair<String, Any?>>::toString)
//            }
        }
    }
