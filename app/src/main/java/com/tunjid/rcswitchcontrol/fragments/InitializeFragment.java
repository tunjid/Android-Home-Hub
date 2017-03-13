package com.tunjid.rcswitchcontrol.fragments;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment;

import static android.app.Activity.RESULT_OK;

public class InitializeFragment extends BaseFragment
        implements View.OnClickListener {

    // Declare final integers
    private final int REQUEST_ENABLE_BT = 2;
    private final int BLUETOOTH_ALREADY_ON = 10;

    private TextView infoText;
    private Button pairButton;
    private ProgressBar progressBar;

    public static InitializeFragment newInstance() {
        InitializeFragment fragment = new InitializeFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_pair, container, false);

        infoText = (TextView) rootView.findViewById(R.id.bluetooth_prompt);
        pairButton = (Button) rootView.findViewById(R.id.button_pair);
        progressBar = (ProgressBar) rootView.findViewById(R.id.bluetooth_prompt_progressbar);

        infoText.setText(R.string.bt_attempt);
        pairButton.setOnClickListener(this);

        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        getToolBar().setTitle(R.string.initialize);

        BluetoothManager bluetoothManager =
                (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);

        BluetoothAdapter mBluetoothAdapter = bluetoothManager.getAdapter();

        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);

        // Check if BT is on, if not request to turn it on
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // if BT is already on, prompt user to go ahead and scan
        else if (mBluetoothAdapter.isEnabled()) {
            onActivityResult(REQUEST_ENABLE_BT, BLUETOOTH_ALREADY_ON, enableBtIntent);
        }
    }

    // Processes BT request result
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            infoText.setText(R.string.bt_turned_on);
            pairButton.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
        }

        else if (requestCode == REQUEST_ENABLE_BT && resultCode == BLUETOOTH_ALREADY_ON) {
            infoText.setText(R.string.bt_already_on);
            pairButton.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
        }

        else {
            infoText.setText(R.string.bt_denied);
            progressBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        infoText = null;
        pairButton = null;
        progressBar = null;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_pair:
                showFragment(ScanFragment.newInstance());
                break;
        }
    }
}
