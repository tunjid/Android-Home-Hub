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


import android.content.Intent
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import com.tunjid.androidx.core.text.scale
import com.tunjid.androidx.navigation.activityNavigatorController
import com.tunjid.androidx.recyclerview.listAdapterOf
import com.tunjid.androidx.recyclerview.verticalLayoutManager
import com.tunjid.androidx.recyclerview.viewbinding.BindingViewHolder
import com.tunjid.androidx.recyclerview.viewbinding.viewHolderFrom
import com.tunjid.globalui.UiState
import com.tunjid.globalui.uiState
import com.tunjid.globalui.updatePartial
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.common.mapDistinct
import com.tunjid.rcswitchcontrol.databinding.FragmentNsdScanBinding
import com.tunjid.rcswitchcontrol.databinding.ViewholderNsdListBinding
import com.tunjid.rcswitchcontrol.di.viewModelFactory
import com.tunjid.rcswitchcontrol.navigation.AppNavigator
import com.tunjid.rcswitchcontrol.client.ClientNsdService
import com.tunjid.rcswitchcontrol.control.ControlFragment

/**
 * A [androidx.fragment.app.Fragment] listing supported NSD servers
 */
class HostScanFragment : Fragment(R.layout.fragment_nsd_scan) {

    private val viewModel by viewModelFactory<NsdScanViewModel>()
    private val isScanning: Boolean get() = viewModel.state.value?.isScanning == true
    private val navigator by activityNavigatorController<AppNavigator>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        uiState = UiState(
                toolbarShows = true,
                toolbarTitle = getString(R.string.app_name),
                toolbarMenuRes = R.menu.menu_nsd_scan,
                toolbarMenuRefresher = ::onToolbarRefreshed,
                toolbarMenuClickListener = ::onToolbarMenuItemSelected,
        )
        val binding = FragmentNsdScanBinding.bind(view)

        binding.list.apply {
            val listAdapter = listAdapterOf(
                    initialItems = viewModel.state.value?.items ?: listOf(),
                    viewHolderCreator = { parent, _ ->
                        parent.viewHolderFrom(ViewholderNsdListBinding::inflate).apply {
                            this.binding.title.setOnClickListener { onServiceClicked(item.info) }
                        }
                    },
                    viewHolderBinder = { holder, service, _ -> holder.bind(service) }
            )

            layoutManager = verticalLayoutManager()
            adapter = listAdapter

            viewModel.state.apply {
                mapDistinct(NSDState::items).observe(viewLifecycleOwner, listAdapter::submitList)
                mapDistinct(NSDState::isScanning).observe(viewLifecycleOwner) { ::uiState.updatePartial { copy(toolbarInvalidated = true) } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        scanDevices(true)
    }

    override fun onPause() {
        super.onPause()
        scanDevices(false)
    }

    private fun onToolbarRefreshed(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.menu_stop)?.isVisible = isScanning
        menu.findItem(R.id.menu_scan)?.isVisible = !isScanning

        val refresh = menu.findItem(R.id.menu_refresh)

        refresh?.isVisible = isScanning
        if (isScanning) refresh?.setActionView(R.layout.actionbar_indeterminate_progress)
    }

    private fun onToolbarMenuItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.menu_scan -> scanDevices(true)
        R.id.menu_stop -> scanDevices(false)
        else -> Unit
    }

    private fun onServiceClicked(serviceInfo: NsdServiceInfo) {
        val intent = Intent(context, ClientNsdService::class.java)
        intent.putExtra(ClientNsdService.NSD_SERVICE_INFO_KEY, serviceInfo)
        requireContext().startService(intent)

        navigator.push(ControlFragment.newInstance())
    }

    private fun scanDevices(enable: Boolean) {
        if (enable) viewModel.findDevices()
        else viewModel.stopScanning()
    }

    companion object {

        fun newInstance(): HostScanFragment {
            val fragment = HostScanFragment()
            val bundle = Bundle()

            fragment.arguments = bundle
            return fragment
        }
    }
}

private var BindingViewHolder<ViewholderNsdListBinding>.item by BindingViewHolder.Prop<NsdItem>()

private fun BindingViewHolder<ViewholderNsdListBinding>.bind(item: NsdItem) {
    this.item = item
    binding.title.text = SpannableStringBuilder()
            .append(item.info.serviceName)
            .append("\n")
            .append(item.info.host.hostAddress.scale(0.8F))
}