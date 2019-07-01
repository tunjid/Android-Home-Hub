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
import com.tunjid.rcswitchcontrol.adapters.DeviceViewHolder
import com.tunjid.rcswitchcontrol.adapters.RemoteSwitchAdapter
import com.tunjid.rcswitchcontrol.adapters.ZigBeeDeviceViewHolder
import com.tunjid.rcswitchcontrol.data.Device
import com.tunjid.rcswitchcontrol.data.RcSwitch
import com.tunjid.rcswitchcontrol.data.ZigBeeDevice
import com.tunjid.rcswitchcontrol.data.persistence.Converter.Companion.serialize
import com.tunjid.rcswitchcontrol.dialogfragments.RenameSwitchDialogFragment
import com.tunjid.rcswitchcontrol.nsd.protocols.ZigBeeProtocol
import com.tunjid.rcswitchcontrol.services.ClientBleService
import com.tunjid.rcswitchcontrol.utils.DeletionHandler
import com.tunjid.rcswitchcontrol.utils.SpanCountCalculator
import com.tunjid.rcswitchcontrol.viewmodels.NsdClientViewModel

typealias ViewHolder = DeviceViewHolder<out InteractiveAdapter.AdapterListener, out Any>

class NsdSwitchFragment : BaseFragment(),
        RemoteSwitchAdapter.Listener,
        RenameSwitchDialogFragment.SwitchNameListener {

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
                .withAdapter(RemoteSwitchAdapter(this, viewModel.devices))
                .withSwipeDragOptions(SwipeDragOptionsBuilder<ViewHolder>()
                        .setSwipeConsumer { viewHolder, _ -> onDelete(viewHolder) }
                        .setMovementFlagsFunction(this::swipeDirection)
                        .setItemViewSwipeSupplier { true }
                        .build())
                .withInconsistencyHandler(this::onInconsistentList)
                .build()

        return root
    }

    override fun onResume() {
        super.onResume()
        disposables.add(viewModel.listen { it.isRc }.subscribe({ listManager.onDiff(it.result) }, Throwable::printStackTrace))
    }

    override fun onDestroyView() {
        listManager.clear()
        super.onDestroyView()
    }

    override fun onLongClicked(rcSwitch: RcSwitch) =
            RenameSwitchDialogFragment.newInstance(rcSwitch).show(childFragmentManager, "")

    override fun onSwitchToggled(rcSwitch: RcSwitch, state: Boolean) =
            viewModel.dispatchPayload(rcSwitch.key) {
                action = ClientBleService.ACTION_TRANSMITTER
                data = rcSwitch.getEncodedTransmission(state)
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
            .showAlphaSlider(true)
            .density(12)
            .setOnColorChangedListener {
                device.colorCommand(it).let { args ->
                    viewModel.dispatchPayload(ZigBeeProtocol::class.java.simpleName) {
                        action = args.command
                        data = args.serialize()
                    }
                }
            }
            .build()
            .show()

    override fun onSwitchRenamed(rcSwitch: RcSwitch) {
        listManager.notifyItemChanged(viewModel.devices.indexOf(rcSwitch))
        viewModel.dispatchPayload(rcSwitch.key) {
            action = getString(R.string.blercprotocol_rename_command)
            data = rcSwitch.serialize()
        }
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
            if (self.hasItems() && self.peek() is RcSwitch) self.pop().also { device ->
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
