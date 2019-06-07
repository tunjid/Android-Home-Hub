package com.tunjid.rcswitchcontrol.fragments;

import android.os.Bundle;

import com.tunjid.rcswitchcontrol.viewmodels.NsdClientViewModel;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProviders;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;

public class ThingsFragment extends ClientBleFragment {


    public static ThingsFragment newInstance() {
        ThingsFragment startFragment = new ThingsFragment();
        Bundle args = new Bundle();
        startFragment.setArguments(args);
        return startFragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ViewModelProviders.of(this).get(NsdClientViewModel.class);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Request permission for location to enable ble scanning
        requestPermissions(new String[]{ACCESS_COARSE_LOCATION}, 0);
    }
}
