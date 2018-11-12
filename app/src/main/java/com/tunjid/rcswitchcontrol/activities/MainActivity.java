package com.tunjid.rcswitchcontrol.activities;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;

import com.tunjid.rcswitchcontrol.App;
import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.abstractclasses.BaseActivity;
import com.tunjid.rcswitchcontrol.fragments.ClientBleFragment;
import com.tunjid.rcswitchcontrol.fragments.ClientNsdFragment;
import com.tunjid.rcswitchcontrol.fragments.StartFragment;
import com.tunjid.rcswitchcontrol.fragments.ThingsFragment;
import com.tunjid.rcswitchcontrol.model.RcSwitch;
import com.tunjid.rcswitchcontrol.services.ClientBleService;
import com.tunjid.rcswitchcontrol.services.ClientNsdService;

import static com.tunjid.rcswitchcontrol.services.ClientBleService.BLUETOOTH_DEVICE;

public class MainActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        SharedPreferences preferences = getSharedPreferences(RcSwitch.SWITCH_PREFS, MODE_PRIVATE);

        String lastConnectedDevice = preferences.getString(ClientBleService.LAST_PAIRED_DEVICE, "");
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        Intent startIntent = getIntent();

        // Retreive device from notification intent or shared preferences
        BluetoothDevice device = startIntent.hasExtra(BLUETOOTH_DEVICE)
                ? (BluetoothDevice) startIntent.getParcelableExtra(BLUETOOTH_DEVICE)
                : !TextUtils.isEmpty(lastConnectedDevice) && bluetoothAdapter != null && bluetoothAdapter.isEnabled()
                ? bluetoothAdapter.getRemoteDevice(lastConnectedDevice)
                : null;

        boolean isSavedInstance = savedInstanceState != null;
        boolean isNullDevice = device == null;
        boolean isNsdClient = startIntent.hasExtra(ClientNsdService.NSD_SERVICE_INFO_KEY)
                || !TextUtils.isEmpty(preferences.getString(ClientNsdService.LAST_CONNECTED_SERVICE, ""));

        if (!isNullDevice) {
            Intent intent = new Intent(this, ClientBleService.class);
            intent.putExtra(BLUETOOTH_DEVICE, device);
            startService(intent);
        }
        if (isNsdClient) {
            sendBroadcast(new Intent(ClientNsdService.ACTION_START_NSD_DISCOVERY));
        }

        if (!isSavedInstance) {
            showFragment(App.isAndroidThings()
                    ? ThingsFragment.newInstance()
                    : isNsdClient
                    ? ClientNsdFragment.newInstance()
                    : isNullDevice
                    ? StartFragment.newInstance()
                    : ClientBleFragment.newInstance(device)
            );
        }
    }
}
