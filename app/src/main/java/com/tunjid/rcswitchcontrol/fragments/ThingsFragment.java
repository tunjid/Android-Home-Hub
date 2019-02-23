package com.tunjid.rcswitchcontrol.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;

public class ThingsFragment extends ClientBleFragment {

    public static ThingsFragment newInstance() {
        ThingsFragment startFragment = new ThingsFragment();
        Bundle args = new Bundle();
        startFragment.setArguments(args);
        return startFragment;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Request permission for location to enable ble scanning
        requestPermissions(new String[]{ACCESS_COARSE_LOCATION}, 0);

    }

    @Override
    protected boolean showsToolBar() {
        return true;
    }

}
