package com.tunjid.rcswitchcontrol.nsd.nsdprotocols;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.ServiceConnection;
import com.tunjid.rcswitchcontrol.bluetooth.BLEScanner;
import com.tunjid.rcswitchcontrol.model.Payload;
import com.tunjid.rcswitchcontrol.model.RcSwitch;
import com.tunjid.rcswitchcontrol.services.ClientBleService;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import static android.content.Context.MODE_PRIVATE;

/**
 * A protocol for Android Things Devices without a screen
 * <p>
 * Created by tj.dahunsi on 4/12/17.
 */

class ScanBleRcProtocol extends CommsProtocol implements BLEScanner.BleScanCallback {

    private static final String TAG = ScanBleRcProtocol.class.getSimpleName();
    private static final int SCAN_DURATION = 5000;

    private final String SCAN;
    private final String CONNECT;
    private final String DISCONNECT;

    private BluetoothDevice currentDevice;
    private final BLEScanner scanner;

    private final Handler scanHandler;
    private final HandlerThread scanThread;

    private final Map<String, BluetoothDevice> deviceMap = new HashMap<>();
    private final ServiceConnection<ClientBleService> bleConnection;
    private final Runnable scanCompleteRunnable = new Runnable() {
        @Override
        public void run() {
            scanner.stopScan();

            Resources resources = appContext.getResources();
            Payload.Builder builder = Payload.builder();
            builder.setKey(ScanBleRcProtocol.this.getClass().getName());
            builder.addCommand(RESET);
            builder.addCommand(SCAN);

            for (BluetoothDevice device : deviceMap.values()) builder.addCommand(device.getName());

            builder.setResponse(resources.getString(R.string.scanblercprotocol_scan_response, deviceMap.size()));

            assert printWriter != null;
            printWriter.println(builder.build().serialize());
        }
    };

    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case ClientBleService.ACTION_GATT_CONNECTED:
                case ClientBleService.ACTION_GATT_CONNECTING:
                case ClientBleService.ACTION_GATT_DISCONNECTED:
                    onConnectionStateChanged(action);
                    break;
            }
            Log.i(TAG, "Received data for: " + action);
        }
    };

    ScanBleRcProtocol(PrintWriter printWriter) {
        super(printWriter);

        SCAN = appContext.getString(R.string.button_scan);
        CONNECT = appContext.getString(R.string.connect);
        DISCONNECT = appContext.getString(R.string.menu_disconnect);

        scanThread = new HandlerThread("Hi");
        scanThread.start();

        scanHandler = new Handler(scanThread.getLooper());
        bleConnection = new ServiceConnection<>(ClientBleService.class);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ClientBleService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(ClientBleService.ACTION_GATT_CONNECTING);
        intentFilter.addAction(ClientBleService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(ClientBleService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(ClientBleService.ACTION_CONTROL);
        intentFilter.addAction(ClientBleService.ACTION_SNIFFER);
        intentFilter.addAction(ClientBleService.DATA_AVAILABLE_UNKNOWN);

        LocalBroadcastManager.getInstance(appContext).registerReceiver(gattUpdateReceiver, intentFilter);

        BluetoothManager bluetoothManager = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        if (!bluetoothAdapter.isEnabled()) bluetoothAdapter.enable();

        scanner = new BLEScanner(this, bluetoothAdapter);

        SharedPreferences preferences = appContext.getSharedPreferences(RcSwitch.SWITCH_PREFS, MODE_PRIVATE);
        String lastConnectedDevice = preferences.getString(ClientBleService.LAST_PAIRED_DEVICE, "");

        // Retreive device from shared preferences if it exists
        if (!TextUtils.isEmpty(lastConnectedDevice) && bluetoothAdapter.isEnabled()) {
            currentDevice = bluetoothAdapter.getRemoteDevice(lastConnectedDevice);
            Bundle extras = new Bundle();
            extras.putParcelable(ClientBleService.BLUETOOTH_DEVICE, currentDevice);

            bleConnection.with(appContext).setExtras(extras).bind();
        }
    }

    @Override
    public Payload processInput(String input) {
        Resources resources = appContext.getResources();
        Payload.Builder builder = Payload.builder();
        builder.setKey(getClass().getName());
        builder.addCommand(RESET);

        if (input == null) input = PING;

        if (input.equals(PING) || input.equals(RESET)) {
            return builder.setResponse(resources.getString(R.string.scanblercprotocol_ping_reponse))
                    .addCommand(SCAN).build();
        }
        else if (input.equals(SCAN)) {
            deviceMap.clear();
            scanner.startScan();
            scanHandler.postDelayed(scanCompleteRunnable, SCAN_DURATION);

            builder.setResponse(resources.getString(R.string.scanblercprotocol_start_scan_reponse));

            return builder.build();
        }
        else if (input.equals(CONNECT) && bleConnection.isBound()) {
            bleConnection.getBoundService().connect(currentDevice);
        }
        else if (input.equals(DISCONNECT) && bleConnection.isBound()) {
            bleConnection.getBoundService().disconnect();
        }
        else if (deviceMap.containsKey(input)) {
            Bundle extras = new Bundle();
            extras.putParcelable(ClientBleService.BLUETOOTH_DEVICE, deviceMap.get(input));

            bleConnection.with(appContext).setExtras(extras).start();
            bleConnection.with(appContext).setExtras(extras).bind();

            Log.i(TAG, "Started ClientBleService, device: " + input);
        }
        return builder.build();
    }

    @Override
    public void onDeviceFound(BluetoothDevice device) {
        device.getName();
        deviceMap.put(device.getName(), device);
    }

    @Override
    public void close() throws IOException {
        LocalBroadcastManager.getInstance(appContext).unregisterReceiver(gattUpdateReceiver);
        scanThread.quitSafely();

        if (bleConnection.isBound()) bleConnection.unbindService();
    }

    private void onConnectionStateChanged(String newState) {
        Payload.Builder builder = Payload.builder();
        builder.setKey(getClass().getSimpleName());
        builder.addCommand(RESET);

        switch (newState) {
            case ClientBleService.ACTION_GATT_CONNECTED:
                builder.setResponse(appContext.getString(R.string.connected))
                        .addCommand(DISCONNECT);
                break;
            case ClientBleService.ACTION_GATT_CONNECTING:
                builder.setResponse(appContext.getString(R.string.connecting));
                break;
            case ClientBleService.ACTION_GATT_DISCONNECTED:
                builder.setResponse(appContext.getString(R.string.disconnected))
                        .addCommand(CONNECT);
                break;
        }
        pushData(builder.build());
    }

    private void pushData(final Payload payload) {
        scanHandler.post(new Runnable() {
            @Override
            public void run() {
                assert printWriter != null;
                printWriter.println(payload.serialize());
            }
        });
    }
}
