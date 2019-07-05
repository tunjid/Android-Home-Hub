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

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.Callback.makeMovementFlags
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import com.tunjid.androidbootstrap.material.animator.FabExtensionAnimator
import com.tunjid.androidbootstrap.recyclerview.ListManager
import com.tunjid.androidbootstrap.recyclerview.ListManagerBuilder
import com.tunjid.androidbootstrap.recyclerview.ListPlaceholder
import com.tunjid.androidbootstrap.recyclerview.SwipeDragOptionsBuilder
import com.tunjid.rcswitchcontrol.App.Companion.isServiceRunning
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment
import com.tunjid.rcswitchcontrol.activities.MainActivity
import com.tunjid.rcswitchcontrol.adapters.DeviceAdapter
import com.tunjid.rcswitchcontrol.data.Device
import com.tunjid.rcswitchcontrol.data.RfSwitch
import com.tunjid.rcswitchcontrol.data.ZigBeeDevice
import com.tunjid.rcswitchcontrol.dialogfragments.NameServiceDialogFragment
import com.tunjid.rcswitchcontrol.dialogfragments.RenameSwitchDialogFragment
import com.tunjid.rcswitchcontrol.services.ClientBleService.Companion.BLUETOOTH_DEVICE
import com.tunjid.rcswitchcontrol.services.ServerNsdService
import com.tunjid.rcswitchcontrol.utils.DeletionHandler
import com.tunjid.rcswitchcontrol.utils.SpanCountCalculator
import com.tunjid.rcswitchcontrol.viewmodels.BleClientViewModel

open class ClientBleFragment : BaseFragment(),
        DeviceAdapter.Listener,
        RenameSwitchDialogFragment.SwitchNameListener,
        NameServiceDialogFragment.ServiceNameListener {

    private var lastOffSet: Int = 0
    private var isDeleting: Boolean = false

    private lateinit var progressBar: View
    private lateinit var connectionStatus: TextView
    private lateinit var listManager: ListManager<ViewHolder, ListPlaceholder<*>>

    private lateinit var viewModel: BleClientViewModel

    override val fabState: FabExtensionAnimator.GlyphState
        get() = viewModel.fabState

    override val fabClickListener: View.OnClickListener
        get() = View.OnClickListener {
            toggleProgress(true)
            viewModel.sniffRcSwitch()
        }

    private val swipeDirection: Int
        get() = if (isDeleting) 0 else makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        viewModel = ViewModelProviders.of(requireActivity()).get(BleClientViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        val root = inflater.inflate(R.layout.fragment_ble_client, container, false)
        val appBarLayout = root.findViewById<AppBarLayout>(R.id.app_bar_layout)

        progressBar = root.findViewById(R.id.progress_bar)
        connectionStatus = root.findViewById(R.id.connection_status)

        listManager = ListManagerBuilder<ViewHolder, ListPlaceholder<*>>()
                .withRecyclerView(root.findViewById(R.id.switch_list))
                .withAdapter(DeviceAdapter(this, viewModel.switches))
                .withGridLayoutManager(SpanCountCalculator.spanCount)
                .addScrollListener { _, dy -> if (dy != 0) toggleFab(dy < 0) }
                .withSwipeDragOptions(SwipeDragOptionsBuilder<ViewHolder>()
                        .setMovementFlagsFunction { swipeDirection }
                        .setSwipeConsumer { viewHolder, _ -> onDelete(viewHolder) }
                        .build())
                .build()

        appBarLayout.addOnOffsetChangedListener(object : AppBarLayout.OnOffsetChangedListener {
            override fun onOffsetChanged(p0: AppBarLayout?, verticalOffset: Int) {
                if (verticalOffset == 0) return
                toggleFab(verticalOffset < lastOffSet)
                lastOffSet = verticalOffset
            }
        })

        return root
    }

    override fun onResume() {
        super.onResume()
        toolBar.setTitle(R.string.switches)

        disposables.add(viewModel.connectionState().subscribe(this::onConnectionStateChanged, Throwable::printStackTrace))
        disposables.add(viewModel.listenServer().subscribe({ requireActivity().invalidateOptionsMenu() }, Throwable::printStackTrace))
        arguments!!.getParcelable<BluetoothDevice>(BLUETOOTH_DEVICE)?.let {
            disposables.add(viewModel.listenBle(it).subscribe(this::toggleProgress, Throwable::printStackTrace))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_ble_client, menu)

        val bleConnected = viewModel.isBleConnected
        val serviceRunning = isServiceRunning(ServerNsdService::class.java)

        menu.findItem(R.id.menu_refresh).isVisible = bleConnected
        menu.findItem(R.id.menu_connect).isVisible = !bleConnected
        menu.findItem(R.id.menu_disconnect).isVisible = bleConnected
        menu.findItem(R.id.menu_start_nsd).isVisible = !serviceRunning
        menu.findItem(R.id.menu_restart_nsd).isVisible = serviceRunning

        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (!viewModel.isBleBound) return super.onOptionsItemSelected(item)

        when (item.itemId) {
            R.id.menu_refresh -> {
                viewModel.refreshSwitches()
                listManager.notifyDataSetChanged()
                return true
            }
            R.id.menu_connect -> {
                viewModel.reconnectBluetooth()
                return true
            }
            R.id.menu_disconnect -> {
                viewModel.disconnectBluetooth()
                return true
            }
            R.id.menu_start_nsd -> NameServiceDialogFragment.newInstance().show(childFragmentManager, "")
            R.id.menu_restart_nsd -> viewModel.restartServer()
            R.id.menu_forget -> {
                viewModel.forgetBluetoothDevice()

                val activity = requireActivity()
                activity.finish()

                startActivity(Intent(activity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onDestroyView() {
        listManager.clear()
        super.onDestroyView()
    }

    override fun showsFab(): Boolean = true

    override fun onLongClicked(rfSwitch: RfSwitch) =
            RenameSwitchDialogFragment.newInstance(rfSwitch).show(childFragmentManager, "")

    override fun onSwitchToggled(rfSwitch: RfSwitch, state: Boolean) =
            viewModel.toggleSwitch(rfSwitch, state)

    override fun onSwitchRenamed(rfSwitch: RfSwitch) =
            listManager.notifyItemChanged(viewModel.onSwitchUpdated(rfSwitch))

    override fun onLongClicked(device: ZigBeeDevice) {
    }

    override fun onSwitchToggled(device: ZigBeeDevice, state: Boolean) {
    }

    override fun rediscover(device: ZigBeeDevice) {
    }

    override fun color(device: ZigBeeDevice) {
    }

    override fun level(device: ZigBeeDevice, level: Float) {
    }

    override fun onServiceNamed(name: String) = viewModel.nameServer(name)

    private fun onConnectionStateChanged(status: String) {
        requireActivity().invalidateOptionsMenu()
        connectionStatus.text = status
    }

    private fun toggleProgress(show: Boolean) {
        TransitionManager.beginDelayedTransition(progressBar.parent as ViewGroup, AutoTransition())
        progressBar.visibility = if (show) View.VISIBLE else View.INVISIBLE
        togglePersistentUi()
    }

    private fun onDelete(viewHolder: RecyclerView.ViewHolder) {
        if (isDeleting) return
        isDeleting = true

        val root = view ?: return

        val position = viewHolder.adapterPosition
        val switches = viewModel.switches

        val deletionHandler = DeletionHandler<Device>(position) {
            isDeleting = false
            viewModel.saveSwitches()
        }

        deletionHandler.push(switches[position])
        switches.removeAt(position)
        listManager.notifyItemRemoved(position)

        Snackbar.make(root, R.string.deleted_switch, Snackbar.LENGTH_LONG)
                .addCallback(deletionHandler)
                .setAction(R.string.undo) {
                    if (deletionHandler.hasItems()) {
                        val deletedAt = deletionHandler.deletedPosition
                        switches.add(deletedAt, deletionHandler.pop())
                        listManager.notifyItemInserted(deletedAt)

                        isDeleting = false
                    }
                }
                .show()
    }

    companion object {

        fun newInstance(bluetoothDevice: BluetoothDevice): ClientBleFragment {
            val fragment = ClientBleFragment()
            val args = Bundle()
            args.putParcelable(BLUETOOTH_DEVICE, bluetoothDevice)
            fragment.arguments = args
            return fragment
        }
    }
}
