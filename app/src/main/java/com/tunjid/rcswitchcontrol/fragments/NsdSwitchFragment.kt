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

package com.tunjid.rcswitchcontrol.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.Callback.makeMovementFlags
import androidx.recyclerview.widget.RecyclerView
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.builder.ColorPickerDialogBuilder
import com.tunjid.androidbootstrap.recyclerview.*
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment
import com.tunjid.rcswitchcontrol.adapters.DeviceAdapter
import com.tunjid.rcswitchcontrol.adapters.DeviceViewHolder
import com.tunjid.rcswitchcontrol.adapters.ZigBeeDeviceViewHolder
import com.tunjid.rcswitchcontrol.data.Device
import com.tunjid.rcswitchcontrol.data.RfSwitch
import com.tunjid.rcswitchcontrol.data.ZigBeeDevice
import com.tunjid.rcswitchcontrol.data.persistence.Converter.Companion.serialize
import com.tunjid.rcswitchcontrol.dialogfragments.RenameSwitchDialogFragment
import com.tunjid.rcswitchcontrol.services.ClientBleService
import com.tunjid.rcswitchcontrol.utils.DeletionHandler
import com.tunjid.rcswitchcontrol.utils.SpanCountCalculator
import com.tunjid.rcswitchcontrol.viewmodels.NsdClientViewModel
import com.tunjid.rcswitchcontrol.viewmodels.NsdClientViewModel.State

typealias ViewHolder = DeviceViewHolder<out InteractiveAdapter.AdapterListener, out Any>

class NsdSwitchFragment : BaseFragment(),
        DeviceAdapter.Listener,
        RenameSwitchDialogFragment.SwitchNameListener{

    private var isDeleting: Boolean = false

    private lateinit var viewModel: NsdClientViewModel
    private lateinit var listManager: ListManager<ViewHolder, ListPlaceholder<*>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(parentFragment!!).get(NsdClientViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        val root = inflater.inflate(R.layout.fragment_list, container, false)
        listManager = ListManagerBuilder<ViewHolder, ListPlaceholder<*>>()
                .withRecyclerView(root.findViewById(R.id.list))
                .withGridLayoutManager(SpanCountCalculator.spanCount)
                .withAdapter(DeviceAdapter(this, viewModel.devices))
                .withSwipeDragOptions(SwipeDragOptionsBuilder<ViewHolder>()
                        .setSwipeConsumer { viewHolder, _ -> onDelete(viewHolder) }
                        .setMovementFlagsFunction(this::swipeDirection)
                        .setItemViewSwipeSupplier { true }
                        .build())
                .withInconsistencyHandler(this::onInconsistentList)
                .build()

        return root
    }

    override fun onStart() {
        super.onStart()
        disposables.add(viewModel.listen { it is State.Devices }.subscribe(this::onPayloadReceived, Throwable::printStackTrace))
    }

    override fun onDestroyView() {
        listManager.clear()
        super.onDestroyView()
    }

    override fun onLongClicked(rfSwitch: RfSwitch) =
            RenameSwitchDialogFragment.newInstance(rfSwitch).show(childFragmentManager, "")

    override fun onSwitchToggled(rfSwitch: RfSwitch, state: Boolean) =
            viewModel.dispatchPayload(rfSwitch.key) {
                action = ClientBleService.ACTION_TRANSMITTER
                data = rfSwitch.getEncodedTransmission(state)
            }

    override fun onLongClicked(device: ZigBeeDevice) {
    }

    override fun onSwitchToggled(device: ZigBeeDevice, state: Boolean) =
            device.toggleCommand(state).let {
                viewModel.dispatchPayload(device.key) {
                    action = it.command
                    data = it.serialize()
                }
            }

    override fun rediscover(device: ZigBeeDevice) =
            device.rediscoverCommand().let { args ->
                viewModel.dispatchPayload(device.key) {
                    action = args.command
                    data = args.serialize()
                }
            }

    override fun color(device: ZigBeeDevice) = ColorPickerDialogBuilder
            .with(context)
            .setTitle("Choose color")
            .wheelType(ColorPickerView.WHEEL_TYPE.CIRCLE)
            .showLightnessSlider(true)
            .density(16)
            .setOnColorChangedListener {
                device.colorCommand(it).let { args ->
                    viewModel.dispatchPayload(device.key) {
                        action = args.command
                        data = args.serialize()
                    }
                }
            }
            .build()
            .show()

    override fun level(device: ZigBeeDevice, level: Float) = device.levelCommand(level).let { args ->
        viewModel.dispatchPayload(device.key) {
            action = args.command
            data = args.serialize()
        }
    }

    override fun onSwitchRenamed(rfSwitch: RfSwitch) {
        listManager.notifyItemChanged(viewModel.devices.indexOf(rfSwitch))
        viewModel.dispatchPayload(rfSwitch.key) {
            action = getString(R.string.blercprotocol_rename_command)
            data = rfSwitch.serialize()
        }
    }

    private fun onPayloadReceived(state: State) {
        listManager.onDiff(state.result)
    }

    private fun swipeDirection(holder: ViewHolder): Int =
            if (isDeleting || holder is ZigBeeDeviceViewHolder) 0
            else makeMovementFlags(0, ItemTouchHelper.LEFT)

    private fun onDelete(viewHolder: RecyclerView.ViewHolder) {
        if (isDeleting) return
        isDeleting = true

        if (view == null) return

        val position = viewHolder.adapterPosition

        val devices = viewModel.devices
        val deletionHandler = DeletionHandler<Device>(position) { self ->
            if (self.hasItems() && self.peek() is RfSwitch) self.pop().also { device ->
                viewModel.dispatchPayload(device.key) {
                    action = getString(R.string.blercprotocol_delete_command)
                    data = device.serialize()
                }
            }
            isDeleting = false
        }

        deletionHandler.push(devices[position])
        devices.removeAt(position)
        listManager.notifyItemRemoved(position)

        showSnackBar { snackBar ->
            snackBar.setText(R.string.deleted_switch)
                    .addCallback(deletionHandler)
                    .setAction(R.string.undo) {
                        if (deletionHandler.hasItems()) {
                            val deletedAt = deletionHandler.deletedPosition
                            devices.add(deletedAt, deletionHandler.pop())
                            listManager.notifyItemInserted(deletedAt)
                        }
                        isDeleting = false
                    }
        }
    }

    companion object {

        fun newInstance(): NsdSwitchFragment {
            val fragment = NsdSwitchFragment()
            val bundle = Bundle()

            fragment.arguments = bundle
            return fragment
        }
    }
}
