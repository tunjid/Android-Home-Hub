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

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import com.google.android.material.bottomsheet.setupForBottomSheet
import com.rcswitchcontrol.zigbee.models.ZigBeeCommand
import com.rcswitchcontrol.zigbee.models.payload
import com.tunjid.androidx.core.content.colorAt
import com.tunjid.androidx.core.delegates.fragmentArgs
import com.tunjid.androidx.core.delegates.viewLifecycle
import com.tunjid.globalui.InsetFlags
import com.tunjid.globalui.UiState
import com.tunjid.globalui.liveUiState
import com.tunjid.globalui.uiState
import com.tunjid.globalui.updatePartial
import com.tunjid.rcswitchcontrol.MainActivity
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.client.ClientLoad
import com.tunjid.rcswitchcontrol.client.ClientState
import com.tunjid.rcswitchcontrol.client.Page
import com.tunjid.rcswitchcontrol.client.Status
import com.tunjid.rcswitchcontrol.common.mapDistinct
import com.tunjid.rcswitchcontrol.client.Page.HISTORY
import com.tunjid.rcswitchcontrol.client.ProtocolKey
import com.tunjid.rcswitchcontrol.client.isConnected
import com.tunjid.rcswitchcontrol.client.keys
import com.tunjid.rcswitchcontrol.databinding.FragmentControlBinding
import com.tunjid.rcswitchcontrol.di.activityViewModelFactory
import com.tunjid.rcswitchcontrol.di.dagger
import com.tunjid.rcswitchcontrol.models.Broadcast
import com.tunjid.rcswitchcontrol.server.ServerNsdService
import com.tunjid.rcswitchcontrol.utils.FragmentTabAdapter
import com.tunjid.rcswitchcontrol.utils.attach

class ControlFragment : Fragment(R.layout.fragment_control), ZigBeeArgumentDialogFragment.ZigBeeArgsListener {

    private val viewBinding by viewLifecycle(FragmentControlBinding::bind)
    private val viewModel by activityViewModelFactory<ControlViewModel>()
    private var load by fragmentArgs<ClientLoad>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.load(load)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        uiState = UiState(
            toolbarShows = true,
            toolbarTitle = getString(R.string.switches),
            toolbarMenuRes = R.menu.menu_fragment_nsd_client,
            toolbarMenuRefresher = ::onToolbarRefreshed,
            toolbarMenuClickListener = ::onToolbarMenuItemSelected,
            altToolbarMenuRes = R.menu.menu_alt_devices,
            altToolbarMenuRefresher = ::onToolbarRefreshed,
            altToolbarMenuClickListener = ::onToolbarMenuItemSelected,
            navBarColor = view.context.colorAt(R.color.black_50),
            insetFlags = InsetFlags.NO_BOTTOM
        )

        val bottomSheetBehavior = BottomSheetBehavior.from(viewBinding.bottomSheet)
        val offset = requireContext().resources.getDimensionPixelSize(R.dimen.triple_and_half_margin)

        val calculateTranslation: (slideOffset: Float) -> Float = calculate@{ slideOffset ->
            val peekHeight = bottomSheetBehavior.peekHeight.toFloat()
            when (slideOffset) {
                in Float.MIN_VALUE..0F -> (peekHeight * -slideOffset)
                0F -> peekHeight
                else -> peekHeight + ((viewBinding.bottomSheet.height - peekHeight) * slideOffset)
            }
        }

        val onPageSelected: (position: Int) -> Unit = {
            bottomSheetBehavior.state = if (viewModel.pages[it] == HISTORY) STATE_HALF_EXPANDED else STATE_HIDDEN
        }

        val pageAdapter = FragmentTabAdapter<Page>(this)
        val commandAdapter = FragmentTabAdapter<ProtocolKey>(this)

        viewBinding.mainPager.apply {
            adapter = pageAdapter
            attach(viewBinding.tabs, viewBinding.mainPager, pageAdapter)
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) = onPageSelected(position)
            })
            pageAdapter.submitList(viewModel.pages)
        }

        viewBinding.commandsPager.apply {
            adapter = commandAdapter
            attach(viewBinding.commandTabs, viewBinding.commandsPager, commandAdapter)
            setupForBottomSheet()
        }

        view.doOnLayout {
            liveUiState.mapDistinct { it.systemUI.dynamic.run { topInset to bottomInset } }.observe(viewLifecycleOwner) { (topInset, bottomInset) ->
                viewBinding.bottomSheet.updateLayoutParams { height = it.height - topInset - resources.getDimensionPixelSize(R.dimen.double_and_half_margin) }
                bottomSheetBehavior.peekHeight = resources.getDimensionPixelSize(R.dimen.sextuple_margin) + bottomInset
            }

            bottomSheetBehavior.expandedOffset = offset
            bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    viewBinding.mainPager.updatePadding(bottom = calculateTranslation(slideOffset).toInt())
                }

                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == STATE_HIDDEN && viewModel.pages[viewBinding.mainPager.currentItem] == HISTORY) bottomSheetBehavior.state = STATE_COLLAPSED
                }
            })
            onPageSelected(viewBinding.mainPager.currentItem)
        }

        viewModel.state.apply {
            mapDistinct(ClientState::keys).observe(viewLifecycleOwner, commandAdapter::submitList)
            mapDistinct(ClientState::isNew).observe(viewLifecycleOwner) { isNew ->
                if (isNew) viewBinding.commandsPager.adapter?.notifyDataSetChanged()
            }
            mapDistinct(ClientState::commandInfo).observe(viewLifecycleOwner) {
                if (it != null) ZigBeeArgumentDialogFragment.newInstance(it).show(childFragmentManager, "info")
            }
            mapDistinct(ClientState::connectionStatus).observe(viewLifecycleOwner) { status ->
                ::uiState.updatePartial { copy(toolbarInvalidated = true) }
                viewBinding.connectionStatus.text = resources.getString(R.string.connection_state, status.text)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.onBackground()
    }

    private fun onToolbarRefreshed(menu: Menu) {
        menu.findItem(R.id.menu_ping)?.isVisible = viewModel.state.value.isConnected
        menu.findItem(R.id.menu_connect)?.isVisible = !viewModel.state.value.isConnected
        menu.findItem(R.id.menu_forget)?.isVisible = !ServerNsdService.isServer

        menu.findItem(R.id.menu_rename_device)?.isVisible = viewModel.withSelectedDevices { it.size == 1 }
        menu.findItem(R.id.menu_create_group)?.isVisible = viewModel.withSelectedDevices { it.find { device -> device is Device.RF } == null }
    }

    private fun onToolbarMenuItemSelected(item: MenuItem) {
        if (viewModel.isBound) when (item.itemId) {
            R.id.menu_ping -> viewModel.pingServer().let { true }
            R.id.menu_connect -> dagger.appComponent.broadcaster(Broadcast.ClientNsd.StartDiscovery())
            R.id.menu_forget -> requireActivity().let {
                viewModel.forgetService()

                startActivity(Intent(it, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

                it.finish()
            }
            R.id.menu_rename_device -> RenameSwitchDialogFragment.newInstance(
                viewModel.withSelectedDevices { it.first().editName }
            ).show(childFragmentManager, item.itemId.toString()).let { true }
            R.id.menu_create_group -> GroupDeviceDialogFragment.newInstance.show(childFragmentManager, item.itemId.toString())
            else -> Unit
        }
    }

    private val Status.text: String
        get() = when (this) {
            is Status.Connected -> getString(R.string.connected_to, serviceName)
            is Status.Connecting -> when (serviceName) {
                null -> getString(R.string.connecting)
                else -> getString(R.string.connecting_to, serviceName)
            }
            is Status.Disconnected -> getString(R.string.disconnected)
        }

    override fun onArgsEntered(command: ZigBeeCommand) = viewModel.dispatchPayload(command.payload)

    companion object {
        fun newInstance(load: ClientLoad) = ControlFragment().apply { this.load = load }
    }
}