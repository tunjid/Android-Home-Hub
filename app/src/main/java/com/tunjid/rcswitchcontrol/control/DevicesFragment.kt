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

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.Callback.makeMovementFlags
import androidx.recyclerview.widget.RecyclerView
import com.rcswitchcontrol.zigbee.models.ZigBeeCommand
import com.rcswitchcontrol.zigbee.models.payload
import com.tunjid.androidx.core.delegates.viewLifecycle
import com.tunjid.androidx.navigation.activityNavigatorController
import com.tunjid.androidx.navigation.addOnBackPressedCallback
import com.tunjid.androidx.recyclerview.gridLayoutManager
import com.tunjid.androidx.recyclerview.listAdapterOf
import com.tunjid.androidx.recyclerview.setSwipeDragOptions
import com.tunjid.androidx.recyclerview.viewHolderForItemId
import com.tunjid.androidx.recyclerview.viewbinding.BindingViewHolder
import com.tunjid.androidx.recyclerview.viewbinding.typed
import com.tunjid.androidx.recyclerview.viewbinding.viewHolderFrom
import com.tunjid.globalui.liveUiState
import com.tunjid.globalui.uiState
import com.tunjid.globalui.updatePartial
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.client.ClientState
import com.tunjid.rcswitchcontrol.common.mapDistinct
import com.tunjid.rcswitchcontrol.databinding.FragmentListBinding
import com.tunjid.rcswitchcontrol.databinding.ViewholderPaddingBinding
import com.tunjid.rcswitchcontrol.databinding.ViewholderRemoteSwitchBinding
import com.tunjid.rcswitchcontrol.databinding.ViewholderZigbeeDeviceBinding
import com.tunjid.rcswitchcontrol.di.activityViewModelFactory
import com.tunjid.rcswitchcontrol.navigation.AppNavigator
import com.tunjid.rcswitchcontrol.utils.DeletionHandler
import com.tunjid.rcswitchcontrol.utils.SpanCountCalculator
import com.tunjid.rcswitchcontrol.viewholders.DeviceAdapterListener
import com.tunjid.rcswitchcontrol.viewholders.bind
import com.tunjid.rcswitchcontrol.viewholders.rfDeviceDeviceViewHolder
import com.tunjid.rcswitchcontrol.viewholders.zigbeeDeviceViewHolder

class DevicesFragment : Fragment(R.layout.fragment_list),
    DeviceAdapterListener,
    GroupDeviceDialogFragment.GroupNameListener {

    private var isDeleting: Boolean = false
    private val viewBinding by viewLifecycle(FragmentListBinding::bind)
    private val viewModel by activityViewModelFactory<ControlViewModel>()
    private val navigator by activityNavigatorController<AppNavigator>()

    private val currentDevices get() = viewModel.state.value?.selectedDevices ?: listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addOnBackPressedCallback {
            isEnabled = currentDevices.isEmpty()

            if (!isEnabled) activity?.onBackPressed()
            else viewModel.accept(Input.Sync.ClearSelections)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.list.apply {
            liveUiState.mapDistinct { it.systemUI.dynamic.bottomInset }.observe(viewLifecycleOwner) {
                updatePadding(bottom = it)
            }
            val listAdapter = listAdapterOf(
                initialItems = currentDevices,
                viewHolderCreator = ::createViewHolder,
                viewTypeFunction = ::getDeviceViewType,
                viewHolderBinder = { holder, device, _ ->
                    when (device) {
                        is Device.RF -> holder.typed<ViewholderRemoteSwitchBinding>().bind(device)
                        is Device.ZigBee -> holder.typed<ViewholderZigbeeDeviceBinding>().bind(device)
                    }
                },
                itemIdFunction = { it.hashCode().toLong() }
            )

            layoutManager = gridLayoutManager(spanCount = SpanCountCalculator.spanCount)
            adapter = listAdapter

            setSwipeDragOptions(
                swipeConsumer = { viewHolder: RecyclerView.ViewHolder, _ -> onDelete(viewHolder) },
                movementFlagFunction = ::swipeDirection,
                itemViewSwipeSupplier = { true }
            )

            viewModel.state
                .mapDistinct(ControlState::clientState)
                .mapDistinct(ClientState::devices).apply {
                    observe(viewLifecycleOwner, listAdapter::submitList)
                    observe(viewLifecycleOwner) { refreshUi() }

                    mapDistinct {
                        it.filterIsInstance<Device.ZigBee>()
                            .map(Device.ZigBee::trifecta)
                            .map(Triple<Pair<String, Any?>, Pair<String, Any?>, Pair<String, Any?>>::toString)
                    }
                        .observe(viewLifecycleOwner) {
//                    Log.i("TEST", "Trifecta: \n ${it.joinToString(separator = "\n")}")
                        }
                }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() = ::uiState.updatePartial {
        val selected = viewModel.state.value?.selectedDevices
        copy(
            altToolbarTitle = getString(R.string.devices_selected, currentDevices.size),
            altToolbarShows = !selected.isNullOrEmpty()
        )
    }

    override fun onClicked(device: Device) {
        if (currentDevices.isNotEmpty()) onLongClicked(device)
    }

    override fun onLongClicked(device: Device) {
        viewModel.accept(Input.Sync.Select(device))
    }

    override fun onSwitchToggled(device: Device, isOn: Boolean) = when (device) {
        is Device.RF -> viewModel.accept(Input.Async.ServerCommand(device.togglePayload(isOn)))
        else -> Unit
    }

    override fun send(command: ZigBeeCommand) = viewModel.accept(Input.Async.ServerCommand(command.payload))

    override fun onGroupNamed(groupName: CharSequence) {
        viewModel.accept(Input.Sync.ClearSelections)
    }

    private fun getDeviceViewType(device: Device) = device::class.hashCode()

    private fun createViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
        Device.RF::class.hashCode() -> parent.rfDeviceDeviceViewHolder(this)
        Device.ZigBee::class.hashCode() -> parent.zigbeeDeviceViewHolder(this)
        else -> parent.viewHolderFrom(ViewholderPaddingBinding::inflate)
    }

    private fun swipeDirection(holder: BindingViewHolder<*>): Int =
        if (isDeleting || holder.binding is ViewholderZigbeeDeviceBinding) 0
        else makeMovementFlags(0, ItemTouchHelper.LEFT)

    private fun onDelete(viewHolder: RecyclerView.ViewHolder) {
        if (isDeleting) return
        isDeleting = true

        if (view == null) return

        val position = viewHolder.adapterPosition

        val devices = currentDevices
        val deletionHandler = DeletionHandler<Device>(position) { self ->
            if (self.hasItems() && self.peek() is Device.RF) self.pop().also { device ->
                viewModel.accept(Input.Async.ServerCommand((device as Device.RF).deletePayload))
            }
            isDeleting = false
        }

        deletionHandler.push(devices[position])
//        devices.removeAt(position)
//        listManager.notifyItemRemoved(position)

        navigator.transientBarDriver.showSnackBar { snackBar ->
            snackBar.setText(R.string.deleted_switch)
                .addCallback(deletionHandler)
                .setAction(R.string.undo) {
                    if (deletionHandler.hasItems()) {
                        val deletedAt = deletionHandler.deletedPosition
//                            devices.add(deletedAt, deletionHandler.pop())
//                            listManager.notifyItemInserted(deletedAt)
                    }
                    isDeleting = false
                }
        }
    }

    companion object {
        fun newInstance(): DevicesFragment {
            val fragment = DevicesFragment()
            val bundle = Bundle()

            fragment.arguments = bundle
            return fragment
        }
    }
}
