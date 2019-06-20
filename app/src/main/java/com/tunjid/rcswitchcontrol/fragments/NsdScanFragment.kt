package com.tunjid.rcswitchcontrol.fragments


import android.content.Intent
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.DividerItemDecoration.VERTICAL
import com.tunjid.androidbootstrap.recyclerview.ListManager
import com.tunjid.androidbootstrap.recyclerview.ListManagerBuilder
import com.tunjid.androidbootstrap.recyclerview.ListPlaceholder
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment
import com.tunjid.rcswitchcontrol.adapters.NSDAdapter
import com.tunjid.rcswitchcontrol.services.ClientNsdService
import com.tunjid.rcswitchcontrol.viewmodels.NsdScanViewModel

/**
 * A [androidx.fragment.app.Fragment] listing supported NSD servers
 */
class NsdScanFragment : BaseFragment(), NSDAdapter.ServiceClickedListener {

    private var isScanning: Boolean = false

    private lateinit var scrollManager: ListManager<NSDAdapter.NSDViewHolder, ListPlaceholder<*>>
    private lateinit var viewModel: NsdScanViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        viewModel = ViewModelProviders.of(this).get(NsdScanViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_nsd_scan, container, false)
        scrollManager = ListManagerBuilder<NSDAdapter.NSDViewHolder, ListPlaceholder<*>>()
                .withRecyclerView(root.findViewById(R.id.list))
                .addDecoration(DividerItemDecoration(requireActivity(), VERTICAL))
                .withAdapter(NSDAdapter(this, viewModel.services))
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

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_nsd_scan, menu)

        menu.findItem(R.id.menu_stop).isVisible = isScanning
        menu.findItem(R.id.menu_scan).isVisible = !isScanning

        val refresh = menu.findItem(R.id.menu_refresh)

        refresh.isVisible = isScanning
        if (isScanning) refresh.setActionView(R.layout.actionbar_indeterminate_progress)
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

        showFragment(ClientNsdFragment.newInstance())
    }

    override fun isSelf(serviceInfo: NsdServiceInfo): Boolean {
        return false
    }

    private fun scanDevices(enable: Boolean) {
        isScanning = enable

        if (isScanning) disposables.add(viewModel.findDevices()
                .doOnSubscribe { requireActivity().invalidateOptionsMenu() }
                .doFinally(this::onScanningStopped)
                .subscribe(scrollManager::onDiff, Throwable::printStackTrace))
        else viewModel.stopScanning()
    }

    private fun onScanningStopped() {
        isScanning = false
        requireActivity().invalidateOptionsMenu()
    }

    companion object {

        fun newInstance(): NsdScanFragment {
            val fragment = NsdScanFragment()
            val bundle = Bundle()

            fragment.arguments = bundle
            return fragment
        }
    }
}
