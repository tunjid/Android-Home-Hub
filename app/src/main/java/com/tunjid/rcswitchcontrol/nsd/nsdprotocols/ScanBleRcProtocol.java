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
import android.os.ParcelUuid;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import com.tunjid.androidbootstrap.communications.bluetooth.BLEScanner;
import com.tunjid.androidbootstrap.communications.bluetooth.ScanFilterCompat;
import com.tunjid.androidbootstrap.communications.bluetooth.ScanRecordCompat;
import com.tunjid.androidbootstrap.communications.bluetooth.ScanResultCompat;
import com.tunjid.androidbootstrap.core.components.ServiceConnection;
import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.model.Payload;
import com.tunjid.rcswitchcontrol.model.RcSwitch;
import com.tunjid.rcswitchcontrol.services.ClientBleService;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static android.content.Context.MODE_PRIVATE;

/**
 * A protocol for scanning for BLE devices for remote devices
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

    private final BroadcastReceiver bleUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Payload.Builder builder = Payload.builder();
            builder.setKey(getClass().getSimpleName()).addCommand(RESET);
            switch (action) {
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
            Log.d(TAG, "Received data for: " + action);
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

        LocalBroadcastManager.getInstance(appContext).registerReceiver(bleUpdateReceiver, intentFilter);

        BluetoothManager bluetoothManager = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        if (!bluetoothAdapter.isEnabled()) bluetoothAdapter.enable();

        UUID serviceUUID = UUID.fromString(ClientBleService.DATA_TRANSCEIVER_SERVICE);
        scanner = BLEScanner.getBuilder(bluetoothAdapter)
                .addFilter(ScanFilterCompat.getBuilder()
                        .setServiceUuid(new ParcelUuid(serviceUUID))
                        .build())
                .withCallBack(this)
                .build();

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
    public Payload processInput(Payload input) {
        Resources resources = appContext.getResources();
        Payload.Builder builder = Payload.builder();
        builder.setKey(getClass().getName());
        builder.addCommand(RESET);

        String action = input.getAction();

        if (action.equals(PING) || action.equals(RESET)) {
            builder.setResponse(resources.getString(R.string.scanblercprotocol_ping_reponse))
                    .addCommand(SCAN).build();
        }
        else if (action.equals(SCAN)) {
            deviceMap.clear();
            scanner.startScan();
            scanHandler.postDelayed(scanCompleteRunnable, SCAN_DURATION);

            builder.setResponse(resources.getString(R.string.scanblercprotocol_start_scan_reponse));
        }
        else if (action.equals(CONNECT) && bleConnection.isBound()) {
            bleConnection.getBoundService().connect(currentDevice);
        }
        else if (action.equals(DISCONNECT) && bleConnection.isBound()) {
            bleConnection.getBoundService().disconnect();
        }
        else if (deviceMap.containsKey(action)) {
            Bundle extras = new Bundle();
            extras.putParcelable(ClientBleService.BLUETOOTH_DEVICE, deviceMap.get(action));

            bleConnection.with(appContext).setExtras(extras).start();
            bleConnection.with(appContext).setExtras(extras).bind();

            Log.i(TAG, "Started ClientBleService, device: " + action);
        }
        return builder.build();
    }

    @Override
    public void onDeviceFound(ScanResultCompat result) {
        ScanRecordCompat record = result.getScanRecord();
        BluetoothDevice device = result.getDevice();
        if (record != null) deviceMap.put(record.getDeviceName(), device);
    }

    @Override
    public void close() throws IOException {
        LocalBroadcastManager.getInstance(appContext).unregisterReceiver(bleUpdateReceiver);
        scanThread.quitSafely();

        if (bleConnection.isBound()) bleConnection.unbindService();
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
