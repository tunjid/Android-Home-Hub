package com.tunjid.rcswitchcontrol;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.tunjid.rcswitchcontrol.activities.MainActivity;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;


/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {

    public static final byte STATE_SNIFFING = 0;
    public static final int NOTIFICATION_ID = 1;

    private final static String TAG = BluetoothLeService.class.getSimpleName();

    // Shared preference key
    public static final String SWITCH_PREFS = "SwitchPrefs";

    // Services
    public static final String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    //public static final String DATA_TRANSCEIVER_SERVICE = "195ae58a-437a-489b-b0cd-b7c9c394bae4";

    // Characteristics
    public static final String C_HANDLE_CONTROL = "5fc569a0-74a9-4fa4-b8b7-8354c86e45a4";
    public static final String C_HANDLE_SNIFFER = "21819ab0-c937-4188-b0db-b9621e1696cd";
    public static final String C_HANDLE_TRANSMITTER = "3c79909b-cc1c-4bb9-8595-f99fa98c6503";

    // Keys for data
    public final static String BLUETOOTH_DEVICE = "BLUETOOTH_DEVICE";

    public final static String GATT_CONNECTED = "ACTION_GATT_CONNECTED";
    public final static String GATT_CONNECTING = "ACTION_GATT_CONNECTING";
    public final static String GATT_DISCONNECTED = "ACTION_GATT_DISCONNECTED";
    public final static String GATT_SERVICES_DISCOVERED = "GATT_SERVICES_DISCOVERED";

    public final static String DATA_AVAILABLE_CONTROL = "DATA_AVAILABLE_CONTROL";
    public final static String DATA_AVAILABLE_SNIFFER = "DATA_AVAILABLE_SNIFFER";
    public final static String DATA_AVAILABLE_TRANSMITTER = "DATA_AVAILABLE_TRANSMITTER";

    public final static String DATA_AVAILABLE_UNKNOWN = "ACTION_DATA_AVAILABLE";

    public final static String EXTRA_DATA = "EXTRA_DATA";

    private static final String LAST_PAIRED_DEVICE = "LAST_PAIRED_DEVICE";

    private boolean isInitialized;
    private boolean isUserInApp;

    private String connectionState = GATT_DISCONNECTED;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothDevice connectedDevice;

    // Queue for reading multiple characteristics due to delay induced by callback.
    private Queue<BluetoothGattCharacteristic> readQueue = new LinkedList<>();
    // Queue for writing multiple descriptors due to delay induced by callback.
    private Queue<BluetoothGattDescriptor> writeQueue = new LinkedList<>();
    // Map of characteristics of interest
    private Map<String, BluetoothGattCharacteristic> characteristicMap = new HashMap<>();

    private final IBinder mBinder = new LocalBinder();

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    connectionState = GATT_CONNECTED;
                    bluetoothGatt.discoverServices();

                    // Save this device for connnecting later
                    getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE).edit()
                            .putString(LAST_PAIRED_DEVICE, connectedDevice.getAddress())
                            .apply();

                    // Update the notification
                    if (!isUserInApp) startForeground(NOTIFICATION_ID, connectedNotification());
                    else stopForeground(true);
                    break;
                case BluetoothProfile.STATE_CONNECTING:
                    connectionState = GATT_CONNECTING;
                    broadcastUpdate(connectionState);
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    connectionState = GATT_DISCONNECTED;
                    broadcastUpdate(connectionState);

                    // Remove the notification
                    if (!isUserInApp) stopForeground(true);
                    break;
            }

            Log.i(TAG, "STATE = " + connectionState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            boolean success = status == BluetoothGatt.GATT_SUCCESS;

            if (success) {
                broadcastUpdate(GATT_SERVICES_DISCOVERED);
                findGattServices(getSupportedGattServices());
                broadcastUpdate(connectionState);
            }

            Log.i(TAG, "onServicesDiscovered staus: " + success);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Callback: Wrote GATT Descriptor successfully.");

                // Remove the item that we just wrote
                if (writeQueue.size() > 0) writeQueue.remove();
            }
            else {
                Log.i(TAG, "onCharacteristicRead error: " + status);
            }

            // Read the top of the queue
            if (writeQueue.size() > 0) bluetoothGatt.writeDescriptor(writeQueue.element());
        }

        @Override
        // Checks queue for characteristics to be read and reads them
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {

                // Remove the characterictic that was just read
                if (readQueue.size() > 0) readQueue.remove();

                String uuid = characteristic.getUuid().toString();

                switch (uuid) {
                    case C_HANDLE_CONTROL:
                        broadcastUpdate(DATA_AVAILABLE_CONTROL, characteristic);
                        break;
                    case C_HANDLE_SNIFFER:
                        broadcastUpdate(DATA_AVAILABLE_SNIFFER, characteristic);
                        break;
                    case C_HANDLE_TRANSMITTER:
                        broadcastUpdate(DATA_AVAILABLE_TRANSMITTER, characteristic);
                        break;
                    default:
                        broadcastUpdate(DATA_AVAILABLE_UNKNOWN, characteristic);
                        break;
                }
            }
            else {
                Log.i(TAG, "onCharacteristicRead error: " + status);
            }

            // Read the top of the queue
            if (readQueue.size() > 0) bluetoothGatt.readCharacteristic(readQueue.element());
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

            switch (characteristic.getUuid().toString()) {
                case C_HANDLE_CONTROL:
                    broadcastUpdate(DATA_AVAILABLE_CONTROL, characteristic);
                    break;
                case C_HANDLE_SNIFFER:
                    broadcastUpdate(DATA_AVAILABLE_SNIFFER, characteristic);
                    break;
                case C_HANDLE_TRANSMITTER:
                    broadcastUpdate(DATA_AVAILABLE_TRANSMITTER, characteristic);
                    break;
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        initialize(intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        onAppForeGround();
        initialize(intent);
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        onAppForeGround();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.

        onAppBackground();

        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        close();
    }

    public void initialize(Intent intent) {

        if (isInitialized) return;

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
            showToast(this, R.string.ble_service_adapter_uninitialized);
            return;
        }

        SharedPreferences sharedPreferences = getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE);
        String lastConnectedDevice = sharedPreferences.getString(LAST_PAIRED_DEVICE, "");

        BluetoothDevice bluetoothDevice = intent != null
                ? (BluetoothDevice) intent.getParcelableExtra(BLUETOOTH_DEVICE)
                : !TextUtils.isEmpty(lastConnectedDevice)
                ? bluetoothAdapter.getRemoteDevice(lastConnectedDevice)
                : null;

        if (bluetoothDevice == null) return;

        this.connectedDevice = bluetoothDevice;

        connect(connectedDevice);

        isInitialized = true;

        Log.i(TAG, "Initialized BLE connection");
    }

    public boolean isConnected() {
        return connectionState.equals(GATT_CONNECTED);
    }

    public String getConnectionState() {
        return connectionState;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param bluetoothDevice The device to connect to.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    @TargetApi(Build.VERSION_CODES.M)
    public boolean connect(final BluetoothDevice bluetoothDevice) {
        if (bluetoothAdapter == null || bluetoothDevice == null) {
            showToast(this, R.string.ble_service_adapter_uninitialized);
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (bluetoothDevice.equals(connectedDevice) && bluetoothGatt != null) {

            showToast(this, R.string.ble_service_reconnecting);

            if (bluetoothGatt.connect()) {
                connectionState = GATT_CONNECTING;

                broadcastUpdate(connectionState);
                showToast(this, R.string.connecting);
                return true;
            }
            else {
                showToast(this, R.string.ble_service_failed_to_connect);
                return false;
            }
        }

        // Set the autoConnect parameter to true.
        bluetoothGatt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? bluetoothDevice.connectGatt(this, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
                : bluetoothDevice.connectGatt(this, true, gattCallback);

        connectionState = GATT_CONNECTING;

        broadcastUpdate(connectionState);
        showToast(this, R.string.ble_service_new_connection);

        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            showToast(this, R.string.ble_service_adapter_uninitialized);
            return;
        }
        bluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (bluetoothGatt == null) return;

        bluetoothGatt.close();
        bluetoothGatt = null;
    }

    public void writeGattDescriptor(BluetoothGattDescriptor descriptor) {
        //put the descriptor into the write queue
        writeQueue.add(descriptor);

        //if there is only 1 item in the queue, then write it.  If more than 1, we handle asynchronously in the callback above
        if (writeQueue.size() > 0) bluetoothGatt.writeDescriptor(descriptor);
    }

    public void writeCharacteristicArray(String uuid, byte[] values) {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            showToast(this, R.string.ble_service_adapter_uninitialized);
            return;
        }
        BluetoothGattCharacteristic characteristic = characteristicMap.get(uuid);

        if (characteristic == null) {
            showToast(this, R.string.disconnected);
            return;
        }

        characteristic.setValue(values);
        bluetoothGatt.writeCharacteristic(characteristic);
    }

    public void onAppBackground() {
        isUserInApp = false;

        // Use a notification to tell the user the app is running
        if (isConnected()) startForeground(NOTIFICATION_ID, connectedNotification());
            // Otherwise, remove the notification and wait for a reconnect
        else stopForeground(true);
    }

    public void onAppForeGround() {
        isUserInApp = true;
        stopForeground(true);
    }

    /**
     * Enables or disables notifications or indications on a given characteristic.
     *
     * @param uuid    UUID of the Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */

    public void setCharacteristicIndication(String uuid, boolean enabled) {
        if (bluetoothAdapter == null || bluetoothGatt == null) {
            showToast(this, R.string.ble_service_adapter_uninitialized);
            return;
        }

        BluetoothGattCharacteristic characteristic = characteristicMap.get(uuid);

        bluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));

        descriptor.setValue(enabled
                ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);

        writeGattDescriptor(descriptor);
    }

    /**
     * Used to send broadcasts that don't have attached data
     */
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    /**
     * Used to send broadcasts that have attached data
     */
    private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        final byte[] rawData = characteristic.getValue();

        if (rawData != null && rawData.length > 0) {

            switch (characteristic.getUuid().toString()) {
                case C_HANDLE_CONTROL:
                    intent.putExtra(DATA_AVAILABLE_CONTROL, rawData);
                    break;
                case C_HANDLE_SNIFFER:
                    intent.putExtra(DATA_AVAILABLE_SNIFFER, rawData);
                    break;
                case C_HANDLE_TRANSMITTER:
                    Log.i(TAG, "Transmitted data: ");
                    break;

                default:
                    final StringBuilder stringBuilder = new StringBuilder(rawData.length);
                    for (byte byteChar : rawData) {
                        stringBuilder.append(byteChar);
                        stringBuilder.append(" ");
                    }
                    intent.putExtra(EXTRA_DATA, new String(rawData) + "\n" + stringBuilder.toString());
                    break;
            }
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    private List<BluetoothGattService> getSupportedGattServices() {
        if (bluetoothGatt == null) return null;
        return bluetoothGatt.getServices();
    }

    private void findGattServices(List<BluetoothGattService> gattServices) {

        if (gattServices == null) return;

        HandlerThread handlerThread = new HandlerThread("IndicationSetThread");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {

            List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();

            // Loops through available Characteristics and find the ones I'm interested in
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                final String uuid = gattCharacteristic.getUuid().toString();
                switch (uuid) {
                    case C_HANDLE_CONTROL:
                    case C_HANDLE_SNIFFER:
                    case C_HANDLE_TRANSMITTER:
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                setCharacteristicIndication(uuid, true);
                            }
                        });
                        break;
                }
                characteristicMap.put(uuid, gattCharacteristic);
            }
        }
    }

    private Notification connectedNotification() {

        final Intent resumeIntent = new Intent(this, MainActivity.class);

        resumeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        resumeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        resumeIntent.putExtra(BLUETOOTH_DEVICE, connectedDevice);
        resumeIntent.putExtra(MainActivity.GO_TO_SCAN, !isConnected());

        PendingIntent activityPendingIntent = PendingIntent.getActivity(
                this, 0, resumeIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getText(R.string.connected))
                .setContentText(getText(R.string.connected))
                .setContentIntent(activityPendingIntent);

        return notificationBuilder.build();
    }

    public static void showToast(Context context, int resourceId) {
        Toast.makeText(context, resourceId, Toast.LENGTH_SHORT).show();
    }

    public class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }
}

///**
// * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
// * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
// * callback.
// *
// * @param characteristic The characteristic to read from.
// */
//public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
//
//    if (bluetoothAdapter == null || bluetoothGatt == null) {
//        showToast(this, R.string.ble_service_adapter_uninitialized);
//        return;
//    }
//
//    //put the characteristic into the read queue
//    readQueue.add(characteristic);
//
//    //if there is only 1 item in the queue, then read it.  If more than 1, we handle asynchronously in the callback above
//    //GIVE PRECEDENCE to descriptor writes.  They must all finish first.
//    if (readQueue.size() > 0) {
//        bluetoothGatt.readCharacteristic(characteristic);
//    }
//}