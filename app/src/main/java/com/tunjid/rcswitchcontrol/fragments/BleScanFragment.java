package com.tunjid.rcswitchcontrol.fragments;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.tunjid.androidbootstrap.communications.bluetooth.BLEScanner;
import com.tunjid.androidbootstrap.communications.bluetooth.ScanFilterCompat;
import com.tunjid.androidbootstrap.communications.bluetooth.ScanResultCompat;
import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment;
import com.tunjid.rcswitchcontrol.adapters.ScanAdapter;
import com.tunjid.rcswitchcontrol.services.ClientBleService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;

public class BleScanFragment extends BaseFragment
        implements
        BLEScanner.BleScanCallback,
        ScanAdapter.AdapterListener {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 10000;    // Stops scanning after 10 seconds.

    private boolean isScanning;

    private RecyclerView recyclerView;

    private BLEScanner scanner;
    private List<ScanResultCompat> scanResults = new ArrayList<>();
    private Set<BluetoothDevice> devices = new HashSet<>();

    public static BleScanFragment newInstance() {
        BleScanFragment fragment = new BleScanFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_ble_scan, container, false);
        Context context = root.getContext();

        recyclerView = root.findViewById(R.id.list);
        recyclerView.setAdapter(new ScanAdapter(this, scanResults));
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.addItemDecoration(new DividerItemDecoration(context, DividerItemDecoration.VERTICAL));

        return root;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        getToolBar().setTitle(R.string.button_scan);

        Activity activity = requireActivity();

        boolean hasBle = activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
        BluetoothManager bluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!hasBle || bluetoothAdapter == null) {
            Toast.makeText(activity, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            activity.onBackPressed();
        }

        UUID serviceUUID = UUID.fromString(ClientBleService.DATA_TRANSCEIVER_SERVICE);
        scanner = BLEScanner.getBuilder(bluetoothAdapter)
                .addFilter(ScanFilterCompat.getBuilder()
                        .setServiceUuid(new ParcelUuid(serviceUUID))
                        .build())
                .withCallBack(this)
                .build();
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {

        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_ble_scan, menu);

        menu.findItem(R.id.menu_stop).setVisible(isScanning);
        menu.findItem(R.id.menu_scan).setVisible(!isScanning);

        if (!isScanning) {
            menu.findItem(R.id.menu_refresh).setVisible(false);
        }
        else {
            menu.findItem(R.id.menu_refresh).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(R.layout.actionbar_indeterminate_progress);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                scanResults.clear();
                getAdapter().notifyDataSetChanged();
                scanLeDevice(true);
                break;
            case R.id.menu_stop:
                scanLeDevice(false);
                break;
        }
        return true;
    }

    private RecyclerView.Adapter getAdapter() {
        return recyclerView.getAdapter();
    }

    @Override
    public void onPause() {
        super.onPause();
        scanResults.clear();
        scanLeDevice(false);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Ensures BT is enabled on the device.  If BT is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!scanner.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        boolean noPermit = SDK_INT >= M && ActivityCompat.checkSelfPermission(requireActivity(),
                ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;

        if (noPermit) requestPermissions(new String[]{ACCESS_COARSE_LOCATION}, REQUEST_ENABLE_BT);
        else scanLeDevice(true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT: {
                // If request is cancelled, the result arrays are empty.
                boolean canScan = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;

                if (canScan) scanLeDevice(true);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            requireActivity().onBackPressed();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recyclerView = null;
    }

    @Override
    public void onBluetoothDeviceClicked(final BluetoothDevice bluetoothDevice) {
        if (bluetoothDevice == null) return;
        if (isScanning) scanLeDevice(false);

        FragmentActivity activity = requireActivity();
        final Intent bleServiceIntent = new Intent(activity, ClientBleService.class);

        bleServiceIntent.putExtra(ClientBleService.BLUETOOTH_DEVICE, bluetoothDevice);
        activity.startService(bleServiceIntent);

        showFragment(ClientBleFragment.newInstance(bluetoothDevice));
    }

    @Override
    public void onDeviceFound(ScanResultCompat scanResult) {
        if (!devices.contains(scanResult.getDevice())) {
            devices.add(scanResult.getDevice());
            scanResults.add(scanResult);
            getAdapter().notifyDataSetChanged();
        }
    }

    // Used to menu_ble_scan for BLE devices
    private void scanLeDevice(boolean enable) {
        isScanning = enable;

        if (enable) scanner.startScan();
        else scanner.stopScan();

        requireActivity().invalidateOptionsMenu();

        // Stops  after a pre-defined menu_ble_scan period.
        if (enable) recyclerView.postDelayed(() -> {
            isScanning = false;
            scanner.stopScan();
            if (getActivity() != null) getActivity().invalidateOptionsMenu();
        }, SCAN_PERIOD);
    }
}