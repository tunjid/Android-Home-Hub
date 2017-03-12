package com.tunjid.rcswitchcontrol.activities;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;

import com.tunjid.rcswitchcontrol.BluetoothLeService;
import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.abstractclasses.BaseActivity;
import com.tunjid.rcswitchcontrol.fragments.ControlFragment;
import com.tunjid.rcswitchcontrol.fragments.StartFragment;

public class MainActivity extends BaseActivity {

    public static final String GO_TO_SCAN = "GO_TO_SCAN";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        BluetoothDevice device = getIntent().getParcelableExtra(BluetoothLeService.BLUETOOTH_DEVICE);
        boolean isSavedInstance = savedInstanceState != null;
        boolean isNullDevice = device == null;

        if (!isNullDevice) {
            // Resuming from a notification, start the service in case it was stopped
            Intent intent = new Intent(this, BluetoothLeService.class);
            intent.putExtra(BluetoothLeService.BLUETOOTH_DEVICE, device);
            startService(intent);
        }

        if (!isSavedInstance) {
            if (isNullDevice) showFragment(StartFragment.newInstance());
            else showFragment(ControlFragment.newInstance(device));
        }
    }
}
