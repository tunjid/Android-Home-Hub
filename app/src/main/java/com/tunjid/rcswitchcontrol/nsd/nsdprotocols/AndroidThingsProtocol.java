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
import java.util.List;
import java.util.Map;

import static android.content.Context.MODE_PRIVATE;

/**
 * A protocol for Android Things Devices without a screen
 * <p>
 * Created by tj.dahunsi on 4/12/17.
 */

class AndroidThingsProtocol extends CommsProtocol implements BLEScanner.BleScanCallback {

    private static final String TAG = AndroidThingsProtocol.class.getSimpleName();

    private static final String SCAN = "Scan";
    private static final String SNIFF = "Sniff";
    private static final String DISCONNECT = "Disconnect";
    private static final String CONNECT = "Connect";

//    static final String STATE_CONNECTING = "STATE_CONNECTING";
//    static final String STATE_CONNECTED = "STATE_CONNECTED";
//    static final String STATE_DISCONNECTED = "STATE_DISCONNECTED";

    private static final int SCAN_DURATION = 5000;

    private final BLEScanner scanner;
    private final HandlerThread scanThread;
    private final Handler scanHandler;
    private final RcSwitch.SwitchCreator switchCreator;
    private final Map<String, BluetoothDevice> deviceMap = new HashMap<>();
    private final List<RcSwitch> switches;
    private final ServiceConnection<ClientBleService> bleConnection = new ServiceConnection<>(ClientBleService.class);
    private final Runnable scanCompleteRunnable = new Runnable() {
        @Override
        public void run() {
            scanner.stopScan();

            Resources resources = appContext.getResources();
            Payload.Builder builder = Payload.builder();
            builder.setKey(AndroidThingsProtocol.this.getClass().getName());
            builder.addCommand(RESET);
            builder.addCommand(SNIFF);
            builder.addCommand(SCAN);

            for (BluetoothDevice device : deviceMap.values()) builder.addCommand(device.getName());

            builder.setResponse(resources.getString(R.string.androidthingsprotocol_scan_response, deviceMap.size()));

            assert printWriter != null;
            printWriter.println(builder.build().serialize());
        }
    };

    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Payload.Builder builder = Payload.builder();
            builder.setKey(getClass().getSimpleName());

            switch (action) {
                case ClientBleService.ACTION_GATT_CONNECTED:
                case ClientBleService.ACTION_GATT_CONNECTING:
                case ClientBleService.ACTION_GATT_DISCONNECTED:
                    onConnectionStateChanged(action);
                    break;
                case ClientBleService.ACTION_CONTROL: {
                    byte[] rawData = intent.getByteArrayExtra(ClientBleService.DATA_AVAILABLE_CONTROL);

                    if (rawData[0] == 1) {
                        builder.setResponse(appContext.getString(R.string.androidthingsprotocol_stop_sniff_response))
                                .addCommand(SNIFF).addCommand(RESET);
                        pushData(builder.build());
                    }
                    break;
                }
                case ClientBleService.ACTION_SNIFFER: {
                    byte[] rawData = intent.getByteArrayExtra(ClientBleService.DATA_AVAILABLE_SNIFFER);

                    switch (switchCreator.getState()) {
                        case ON_CODE:
                            switchCreator.withOnCode(rawData);
                            builder.setResponse(appContext.getString(R.string.androidthingsprotocol_sniff_on_response))
                                    .addCommand(SNIFF).addCommand(RESET);
                            pushData(builder.build());
                            break;
                        case OFF_CODE:
                            RcSwitch rcSwitch = switchCreator.withOffCode(rawData);
                            rcSwitch.setName("Switch " + (switches.size() + 1));

                            builder.addCommand(SNIFF).addCommand(RESET);
                            if (!switches.contains(rcSwitch)) {
                                switches.add(rcSwitch);
                                RcSwitch.saveSwitches(switches);
                                builder.setResponse(appContext.getString(R.string.androidthingsprotocol_sniff_off_response));
                            }
                            else {
                                builder.setResponse(appContext.getString(R.string.androidthingsprotocol_sniff_already_exists_response));
                            }
                            pushData(builder.build());
                            break;
                    }
                    break;
                }
            }

            Log.i(TAG, "Received data for: " + action);
        }
    };

    AndroidThingsProtocol(PrintWriter printWriter) {
        super(printWriter);

        scanThread = new HandlerThread("Hi");
        scanThread.start();

        scanHandler = new Handler(scanThread.getLooper());
        switchCreator = new RcSwitch.SwitchCreator();
        switches = RcSwitch.getSavedSwitches();

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
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(lastConnectedDevice);
            Bundle extras = new Bundle();
            extras.putParcelable(ClientBleService.BLUETOOTH_DEVICE, device);

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
            return builder.addCommand(isConnected() ? SNIFF : SCAN)
                    .setResponse(resources.getString(R.string.androidthingsprotocol_ping_reponse)).build();
        }
        else if (input.equals(SCAN)) {
            deviceMap.clear();
            scanner.startScan();
            scanHandler.postDelayed(scanCompleteRunnable, SCAN_DURATION);

            builder.setResponse(resources.getString(R.string.androidthingsprotocol_start_scan_reponse));

            return builder.build();
        }
        else if (input.equals(SNIFF)) {
            builder.setResponse(appContext.getString(R.string.androidthingsprotocol_start_sniff_response))
                    .addCommand(SNIFF).addCommand(DISCONNECT);

            if (bleConnection.isBound()) {
                bleConnection.getBoundService().writeCharacteristicArray(
                        ClientBleService.C_HANDLE_CONTROL,
                        new byte[]{ClientBleService.STATE_SNIFFING});
            }
        }
        else if (input.equals(CONNECT) && bleConnection.isBound()) {
            //bleConnection.getBoundService().connect()
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
                builder.addCommand(SNIFF);
                builder.addCommand(DISCONNECT);
                builder.setResponse(appContext.getString(R.string.connected));
                break;
            case ClientBleService.ACTION_GATT_CONNECTING:
                builder.setResponse(appContext.getString(R.string.connecting));
                break;
            case ClientBleService.ACTION_GATT_DISCONNECTED:
                builder.addCommand(CONNECT);
                builder.setResponse(appContext.getString(R.string.disconnected));
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

    private boolean isConnected() {
        return bleConnection.isBound() && bleConnection.getBoundService().isConnected();
    }
}
