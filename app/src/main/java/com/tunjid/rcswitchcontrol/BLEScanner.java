package com.tunjid.rcswitchcontrol;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.os.Build;

/**
 * BLE Scanner
 * <p>
 * Created by tj.dahunsi on 2/18/17.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class BLEScanner {
    private final boolean hasLollipop;
    private final BleScanCallback callback;
    private final BluetoothAdapter bluetoothAdapter;

    private BluetoothAdapter.LeScanCallback oldCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            callback.onDeviceFound(device);
        }
    };

    private ScanCallback newCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            callback.onDeviceFound(result.getDevice());
        }

        @Override
        public void onScanFailed(int errorCode) {
        }
    };

    public BLEScanner(BleScanCallback callback, BluetoothAdapter bluetoothAdapter) {
        hasLollipop = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
        this.callback = callback;
        this.bluetoothAdapter = bluetoothAdapter;
    }

    public boolean isEnabled() {
        return bluetoothAdapter.isEnabled();
    }

    @SuppressWarnings("deprecation")
    public void startScan() {
        if (hasLollipop) bluetoothAdapter.getBluetoothLeScanner().startScan(newCallback);
        else bluetoothAdapter.startLeScan(oldCallback);
    }

    @SuppressWarnings("deprecation")
    public void stopScan() {
        if (hasLollipop) bluetoothAdapter.getBluetoothLeScanner().stopScan(newCallback);
        else bluetoothAdapter.stopLeScan(oldCallback);
    }

    /**
     * BLE scan callback
     * <p>
     * Created by tj.dahunsi on 2/18/17.
     */
    public interface BleScanCallback {
        void onDeviceFound(BluetoothDevice device);
    }
}
