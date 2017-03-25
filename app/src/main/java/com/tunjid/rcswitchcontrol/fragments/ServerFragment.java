package com.tunjid.rcswitchcontrol.fragments;


import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment;
import com.tunjid.rcswitchcontrol.nsd.services.ServerNsdService;

import static android.content.Context.BIND_AUTO_CREATE;

/**
 * A {@link Fragment} that starts the {@link ServerNsdService}, and is the default state
 */
public class ServerFragment extends BaseFragment
        implements
        ServiceConnection,
        View.OnClickListener {

    public ServerFragment() {
        // Required empty public constructor
    }

    public static ServerFragment newInstance() {
        ServerFragment fragment = new ServerFragment();
        Bundle bundle = new Bundle();

        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        return inflater.inflate(R.layout.fragment_server, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
//        floatingActionButton.setOnClickListener(this);
//        floatingActionButton.show();

        Activity activity = getActivity();

        // Start the server
        Intent server = new Intent(activity, ServerNsdService.class);
        activity.bindService(server, this, BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        // Not used.
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // Not used.
    }

    @Override
    public void onClick(View v) {
//        switch (v.getId()) {
//            case R.id.fab:
//                showFragment(ServerListFragment.newInstance());
//                break;
//        }
    }
}
