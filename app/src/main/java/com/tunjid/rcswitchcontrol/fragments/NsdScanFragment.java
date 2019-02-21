package com.tunjid.rcswitchcontrol.fragments;


import android.content.Intent;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.tunjid.androidbootstrap.recyclerview.ListManagerBuilder;
import com.tunjid.androidbootstrap.recyclerview.ListPlaceholder;
import com.tunjid.androidbootstrap.recyclerview.ListManager;
import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment;
import com.tunjid.rcswitchcontrol.adapters.NSDAdapter;
import com.tunjid.rcswitchcontrol.services.ClientNsdService;
import com.tunjid.rcswitchcontrol.viewmodels.NsdScanViewModel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.DividerItemDecoration;

import static androidx.recyclerview.widget.DividerItemDecoration.VERTICAL;

/**
 * A {@link androidx.fragment.app.Fragment} listing supported NSD servers
 */
public class NsdScanFragment extends BaseFragment
        implements
        NSDAdapter.ServiceClickedListener {

    private boolean isScanning;

    private ListManager<NSDAdapter.NSDViewHolder, ListPlaceholder> scrollManager;
    private NsdScanViewModel viewModel;

    public static NsdScanFragment newInstance() {
        NsdScanFragment fragment = new NsdScanFragment();
        Bundle bundle = new Bundle();

        fragment.setArguments(bundle);
        return fragment;
    }

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        viewModel = ViewModelProviders.of(this).get(NsdScanViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_nsd_scan, container, false);
        scrollManager = new ListManagerBuilder<NSDAdapter.NSDViewHolder, ListPlaceholder>()
                .withRecyclerView(root.findViewById(R.id.list))
                .addDecoration(new DividerItemDecoration(requireActivity(), VERTICAL))
                .withAdapter(new NSDAdapter(this, viewModel.getServices()))
                .withLinearLayoutManager()
                .build();

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        scanDevices(true);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        scrollManager = null;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_nsd_scan, menu);

        menu.findItem(R.id.menu_stop).setVisible(isScanning);
        menu.findItem(R.id.menu_scan).setVisible(!isScanning);

        MenuItem refresh = menu.findItem(R.id.menu_refresh);

        refresh.setVisible(isScanning);
        if (isScanning) refresh.setActionView(R.layout.actionbar_indeterminate_progress);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                scanDevices(true);
                return true;
            case R.id.menu_stop:
                scanDevices(false);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onServiceClicked(NsdServiceInfo serviceInfo) {
        Intent intent = new Intent(getContext(), ClientNsdService.class);
        intent.putExtra(ClientNsdService.NSD_SERVICE_INFO_KEY, serviceInfo);
        requireContext().startService(intent);

        showFragment(ClientNsdFragment.newInstance());
    }

    @Override
    public boolean isSelf(NsdServiceInfo serviceInfo) {
        return false;
    }

    private void scanDevices(boolean enable) {
        isScanning = enable;

        if (isScanning) disposables.add(viewModel.findDevices()
                .doOnSubscribe(__ -> requireActivity().invalidateOptionsMenu())
                .doFinally(this::onScanningStopped)
                .subscribe(scrollManager::onDiff, Throwable::printStackTrace));
        else viewModel.stopScanning();
    }

    private void onScanningStopped() {
        isScanning = false;
        requireActivity().invalidateOptionsMenu();
    }
}
