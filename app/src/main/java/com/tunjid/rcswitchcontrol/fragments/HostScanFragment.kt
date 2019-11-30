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


import android.content.Intent
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.view.*
import androidx.lifecycle.ViewModelProviders
import com.tunjid.androidx.recyclerview.ListManager
import com.tunjid.androidx.recyclerview.ListManagerBuilder
import com.tunjid.androidx.recyclerview.ListPlaceholder
import com.tunjid.androidx.recyclerview.adapterOf
import com.tunjid.androidx.view.util.inflate
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment
import com.tunjid.rcswitchcontrol.viewholders.HostScanViewHolder
import com.tunjid.rcswitchcontrol.viewholders.ServiceClickedListener
import com.tunjid.rcswitchcontrol.viewholders.withPaddedAdapter
import com.tunjid.rcswitchcontrol.services.ClientNsdService
import com.tunjid.rcswitchcontrol.utils.guard
import com.tunjid.rcswitchcontrol.viewmodels.NsdScanViewModel

/**
 * A [androidx.fragment.app.Fragment] listing supported NSD servers
 */
class HostScanFragment : BaseFragment(), ServiceClickedListener {

    private var isScanning: Boolean = false

    private lateinit var scrollManager: ListManager<HostScanViewHolder, ListPlaceholder<*>>
    private lateinit var viewModel: NsdScanViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(NsdScanViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        updateUi(toolBarMenu = R.menu.menu_nsd_scan)

        val root = inflater.inflate(R.layout.fragment_nsd_scan, container, false)
        scrollManager = ListManagerBuilder<HostScanViewHolder, ListPlaceholder<*>>()
                .withRecyclerView(root.findViewById(R.id.list))
                .withPaddedAdapter(adapterOf(
                        itemsSource = viewModel::services,
                        viewHolderCreator = { parent, _ -> HostScanViewHolder(parent.inflate(R.layout.viewholder_nsd_list), this) },
                        viewHolderBinder = { holder, service, _ -> holder.bind(service) }
                ))
                .withLinearLayoutManager()
                .build()

        return root
    }

    override fun onResume() {
        super.onResume()
        scanDevices(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scrollManager.clear()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.menu_stop)?.isVisible = isScanning
        menu.findItem(R.id.menu_scan)?.isVisible = !isScanning

        val refresh = menu.findItem(R.id.menu_refresh)

        refresh?.isVisible = isScanning
        if (isScanning) refresh?.setActionView(R.layout.actionbar_indeterminate_progress)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_scan -> {
                scanDevices(true)
                return true
            }
            R.id.menu_stop -> {
                scanDevices(false)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onServiceClicked(serviceInfo: NsdServiceInfo) {
        val intent = Intent(context, ClientNsdService::class.java)
        intent.putExtra(ClientNsdService.NSD_SERVICE_INFO_KEY, serviceInfo)
        requireContext().startService(intent)

        navigator.push(ControlFragment.newInstance())
    }

    override fun isSelf(serviceInfo: NsdServiceInfo): Boolean {
        return false
    }

    private fun scanDevices(enable: Boolean) {
        isScanning = enable

        if (isScanning) viewModel.findDevices()
                .doOnSubscribe { updateUi(toolbarInvalidated = true) }
                .doFinally(this::onScanningStopped)
                .subscribe(scrollManager::onDiff, Throwable::printStackTrace)
                .guard(lifecycleDisposable)
        else viewModel.stopScanning()
    }

    private fun onScanningStopped() {
        isScanning = false
        updateUi(toolbarInvalidated = true)
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
