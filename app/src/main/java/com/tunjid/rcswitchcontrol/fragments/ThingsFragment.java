package com.tunjid.rcswitchcontrol.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.WindowManager;

import com.tunjid.rcswitchcontrol.ServiceConnection;
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment;
import com.tunjid.rcswitchcontrol.services.ServerNsdService;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;

public class ThingsFragment extends BaseFragment {

    private final ServiceConnection<ServerNsdService> nsdConnection = new ServiceConnection<>(ServerNsdService.class);

    public static ThingsFragment newInstance() {
        ThingsFragment startFragment = new ThingsFragment();
        Bundle args = new Bundle();
        startFragment.setArguments(args);
        return startFragment;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Request permission for location to enable ble scanning
        requestPermissions(new String[]{ACCESS_COARSE_LOCATION}, 0);

        Activity activity = getActivity();

        nsdConnection.with(activity).bind();

        // Prevent Android things device from sleeping.
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (nsdConnection.isBound()) nsdConnection.unbindService();
    }
}
