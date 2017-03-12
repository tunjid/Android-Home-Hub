package com.tunjid.rcswitchcontrol.fragments;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.transition.AutoTransition;
import android.support.transition.TransitionManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.gson.Gson;
import com.tunjid.rcswitchcontrol.BluetoothLeService;
import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment;
import com.tunjid.rcswitchcontrol.adapters.RemoteSwitchAdapter;
import com.tunjid.rcswitchcontrol.model.RfSwitch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.content.Context.BIND_AUTO_CREATE;
import static android.content.Context.MODE_PRIVATE;
import static com.tunjid.rcswitchcontrol.BluetoothLeService.DATA_AVAILABLE_CONTROL;
import static com.tunjid.rcswitchcontrol.BluetoothLeService.DATA_AVAILABLE_SNIFFER;

public class ControlFragment extends BaseFragment
        implements
        ServiceConnection,
        View.OnClickListener,
        RemoteSwitchAdapter.SwitchListener,
        RenameSwitchDialogFragment.SwitchNameListener {

    private static final String TAG = ControlFragment.class.getSimpleName();
    private static final String SWITCH_PREFS = "SwitchPrefs";
    private static final String SWITCHES_KEY = "Switches";

    private static final Gson gson = new Gson();

    private BluetoothDevice bluetoothDevice;
    private BluetoothLeService bluetoothLeService;

    private Button sniffButton;
    private View progressBar;

    private TextView connectionStatus;
    private RecyclerView switchList;
    private List<RfSwitch> switches;

    private RfSwitch.SwitchCreator switchCreator;

    private final IntentFilter intentFilter = new IntentFilter();
    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            switch (action) {
                case BluetoothLeService.GATT_CONNECTED:
                case BluetoothLeService.GATT_CONNECTING:
                case BluetoothLeService.GATT_DISCONNECTED:
                    onConnectionStateChanged(action);
                    break;
                case DATA_AVAILABLE_CONTROL: {
                    byte[] rawData = intent.getByteArrayExtra(BluetoothLeService.DATA_AVAILABLE_CONTROL);

                    toggleProgress(rawData[0] == 0);
                    break;
                }
                case DATA_AVAILABLE_SNIFFER: {
                    byte[] rawData = intent.getByteArrayExtra(BluetoothLeService.DATA_AVAILABLE_SNIFFER);

                    switch (switchCreator.getState()) {
                        case ON_CODE:
                            switchCreator.withOnCode(rawData);
                            break;
                        case OFF_CODE:
                            RfSwitch rfSwitch = switchCreator.withOffCode(rawData);
                            rfSwitch.setName("Switch " + (switches.size() + 1));

                            if (!switches.contains(rfSwitch)) {
                                switches.add(rfSwitch);
                                switchList.getAdapter().notifyDataSetChanged();

                                saveSwitches();
                            }
                            break;
                    }

                    toggleSniffButton();
                    toggleProgress(false);
                    break;
                }
            }

            Log.i(TAG, "Received data for: " + action);
        }
    };

    public static ControlFragment newInstance(BluetoothDevice bluetoothDevice) {
        ControlFragment fragment = new ControlFragment();
        Bundle args = new Bundle();
        args.putParcelable(BluetoothLeService.BLUETOOTH_DEVICE, bluetoothDevice);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        bluetoothDevice = getArguments().getParcelable(BluetoothLeService.BLUETOOTH_DEVICE);

        intentFilter.addAction(BluetoothLeService.GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.GATT_CONNECTING);
        intentFilter.addAction(BluetoothLeService.GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.DATA_AVAILABLE_CONTROL);
        intentFilter.addAction(BluetoothLeService.DATA_AVAILABLE_SNIFFER);
        intentFilter.addAction(BluetoothLeService.DATA_AVAILABLE_UNKNOWN);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        switchCreator = new RfSwitch.SwitchCreator();
        switches = getSavedSwitches();

        View rootView = inflater.inflate(R.layout.fragment_control, container, false);

        sniffButton = (Button) rootView.findViewById(R.id.sniff);
        progressBar = rootView.findViewById(R.id.progress_bar);

        connectionStatus = (TextView) rootView.findViewById(R.id.connection_status);
        switchList = (RecyclerView) rootView.findViewById(R.id.switch_list);

        sniffButton.setOnClickListener(this);

        switchList.setAdapter(new RemoteSwitchAdapter(this, switches));
        switchList.setLayoutManager(new LinearLayoutManager(getActivity()));

        toggleSniffButton();
        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(gattUpdateReceiver, intentFilter);

        Intent intent = new Intent(getActivity(), BluetoothLeService.class);
        intent.putExtra(BluetoothLeService.BLUETOOTH_DEVICE, getArguments().getParcelable(BluetoothLeService.BLUETOOTH_DEVICE));
        getActivity().bindService(intent, this, BIND_AUTO_CREATE);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_fragment_overview, menu);

        if (bluetoothLeService != null) {
            menu.findItem(R.id.menu_connect).setVisible(!bluetoothLeService.isConnected());
            menu.findItem(R.id.menu_disconnect).setVisible(bluetoothLeService.isConnected());
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (bluetoothLeService != null) {
            switch (item.getItemId()) {
                case R.id.menu_connect:
                    bluetoothLeService.connect(bluetoothDevice.getAddress());
                    return true;
                case R.id.menu_disconnect:
                    bluetoothLeService.disconnect();
                    return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(gattUpdateReceiver);
        super.onDestroy();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        bluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
        onConnectionStateChanged(bluetoothLeService.isConnected()
                ? BluetoothLeService.GATT_CONNECTED
                : BluetoothLeService.GATT_DISCONNECTED);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        bluetoothLeService = null;
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.sniff:
                toggleProgress(true);

                bluetoothLeService.writeCharacteristicArray(BluetoothLeService.C_HANDLE_CONTROL,
                        new byte[]{BluetoothLeService.STATE_SNIFFING});
                break;
        }
    }

    @Override
    public void onLongClicked(RfSwitch rfSwitch) {
        RenameSwitchDialogFragment.newInstance(rfSwitch).show(getChildFragmentManager(), "");
    }

    @Override
    public void onSwitchToggled(RfSwitch rfSwitch, boolean state) {
        byte[] code = state ? rfSwitch.getOnCode() : rfSwitch.getOffCode();
        byte[] transmission = new byte[6];

        System.arraycopy(code, 0, transmission, 0, code.length);
        transmission[4] = rfSwitch.getPulseLength();
        transmission[5] = rfSwitch.getBitLength();

        bluetoothLeService.writeCharacteristicArray(BluetoothLeService.C_HANDLE_TRANSMITTER, transmission);
    }

    @Override
    public void onSwitchRenamed(RfSwitch rfSwitch) {
        switchList.getAdapter().notifyItemChanged(switches.indexOf(rfSwitch));
        saveSwitches();
    }

    private void onConnectionStateChanged(String newState) {
        getActivity().invalidateOptionsMenu();
        String text = null;
        switch (newState) {
            case BluetoothLeService.GATT_CONNECTED:
                text = getString(R.string.connected);
                break;
            case BluetoothLeService.GATT_CONNECTING:
                text = getString(R.string.connecting);
                break;
            case BluetoothLeService.GATT_DISCONNECTED:
                text = getString(R.string.disconnected);
                break;
        }
        connectionStatus.setText(getResources().getString(R.string.connection_state, text));
    }

    private void toggleSniffButton() {
        String state = switchCreator.getState() == RfSwitch.State.ON_CODE
                ? getString(R.string.on)
                : getString(R.string.off);
        sniffButton.setText(getResources().getString(R.string.sniff_code, state));
    }

    private void toggleProgress(boolean show) {
        TransitionManager.beginDelayedTransition((ViewGroup) sniffButton.getParent(), new AutoTransition());

        sniffButton.setVisibility(show ? View.INVISIBLE : View.VISIBLE);
        progressBar.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
    }

    private void saveSwitches() {
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE);
        sharedPreferences.edit().putString(SWITCHES_KEY, gson.toJson(switches)).apply();
    }

    private List<RfSwitch> getSavedSwitches() {
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE);
        String jsonString = sharedPreferences.getString(SWITCHES_KEY, "");
        RfSwitch[] array = gson.fromJson(jsonString, RfSwitch[].class);

        return array == null ? new ArrayList<RfSwitch>() : new ArrayList<>(Arrays.asList(array));

    }
}
