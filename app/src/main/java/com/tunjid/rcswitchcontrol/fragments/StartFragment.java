package com.tunjid.rcswitchcontrol.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment;

public class StartFragment extends BaseFragment
        implements View.OnClickListener {

    public static StartFragment newInstance() {
        StartFragment startFragment = new StartFragment();
        Bundle args = new Bundle();
        startFragment.setArguments(args);
        return startFragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_start, container, false);
        View serverButton = rootView.findViewById(R.id.server);
        View clientButton = rootView.findViewById(R.id.client);

        serverButton.setOnClickListener(this);
        clientButton.setOnClickListener(this);

        return rootView;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.server:
                showFragment(InitializeFragment.newInstance());
                break;
            case R.id.client:
                showFragment(ServerListFragment.newInstance());
                break;
        }
    }
}
