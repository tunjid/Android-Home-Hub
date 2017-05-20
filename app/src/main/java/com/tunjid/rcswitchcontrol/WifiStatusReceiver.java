package com.tunjid.rcswitchcontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.NetworkInfo;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import com.tunjid.androidbootstrap.communications.nsd.DiscoveryListener;
import com.tunjid.androidbootstrap.communications.nsd.NsdHelper;
import com.tunjid.androidbootstrap.communications.nsd.ResolveListener;
import com.tunjid.rcswitchcontrol.services.ClientBleService;
import com.tunjid.rcswitchcontrol.services.ClientNsdService;
import com.tunjid.rcswitchcontrol.services.ServerNsdService;

import java.util.concurrent.atomic.AtomicReference;

import static android.content.Context.MODE_PRIVATE;
import static android.content.Context.WIFI_SERVICE;
import static android.net.wifi.WifiManager.EXTRA_NETWORK_INFO;
import static android.net.wifi.WifiManager.EXTRA_SUPPLICANT_CONNECTED;
import static android.net.wifi.WifiManager.WIFI_STATE_ENABLED;
import static com.tunjid.rcswitchcontrol.model.RcSwitch.SWITCH_PREFS;
import static com.tunjid.rcswitchcontrol.services.ServerNsdService.SERVER_FLAG;

public class WifiStatusReceiver extends BroadcastReceiver {

    public static final int SEARCH_LENGTH_MILLIS = 15000;
    private static final String TAG = WifiStatusReceiver.class.getSimpleName();

    private boolean isSearching;

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean flag = false;
        switch (intent.getAction()) {
            case ClientNsdService.ACTION_START_NSD_DISCOVERY:
                flag = ((WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE)).isWifiEnabled();
                break;
            case WifiManager.WIFI_STATE_CHANGED_ACTION:
                flag = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0) == WIFI_STATE_ENABLED;
                break;
            case WifiManager.NETWORK_STATE_CHANGED_ACTION:
                NetworkInfo info = intent.getParcelableExtra(EXTRA_NETWORK_INFO);
                flag = info.isConnected();
                break;
            case WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION:
                flag = intent.getBooleanExtra(EXTRA_SUPPLICANT_CONNECTED, false);
                break;
        }
        if (flag) connectLastNsdService(context);
        else stopClient(context);
    }

    private void connectLastNsdService(final Context context) {
        if (isSearching) return;

        SharedPreferences preferences = context.getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE);

        // If we're running the BLE service, and we were designated as a NSD server, start it.
        if (Application.isServiceRunning(ClientBleService.class) && preferences.getBoolean(SERVER_FLAG, false)) {
            context.startService(new Intent(context, ServerNsdService.class));
            return;
        }

        final String lastServer = preferences.getString(ClientNsdService.LAST_CONNECTED_SERVICE, "");

        if (TextUtils.isEmpty(lastServer)) return;

        final AtomicReference<NsdHelper> helperReference = new AtomicReference<>();

        NsdHelper nsdHelper = NsdHelper.getBuilder(context)
                .setDiscoveryListener(
                        new DiscoveryListener() {
                            @Override
                            public void onServiceFound(NsdServiceInfo service) {
                                super.onServiceFound(service);
                                if (helperReference.get() == null) return;
                                try {
                                    helperReference.get().resolveService(service);
                                }
                                catch (IllegalArgumentException e) {
                                    Log.i(TAG, "IllegalArgumentException trying to resolve NSD service");
                                }
                            }
                        }
                )
                .setResolveListener(
                        new ResolveListener() {
                            @Override
                            public void onServiceResolved(NsdServiceInfo service) {
                                if (helperReference.get() == null) return;
                                if (service.getServiceName().equals(lastServer)) {
                                    Intent intent = new Intent(context, ClientNsdService.class);
                                    intent.putExtra(ClientNsdService.NSD_SERVICE_INFO_KEY, service);
                                    context.startService(intent);

                                    helperReference.get().tearDown();
                                    isSearching = false;
                                }
                            }
                        }
                )
                .build();

        helperReference.set(nsdHelper);
        nsdHelper.discoverServices();
        isSearching = true;

        new Handler(Looper.myLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (helperReference.get() == null) return;
                helperReference.get().tearDown();
                isSearching = false;
            }
        }, SEARCH_LENGTH_MILLIS);
    }

    private void stopClient(Context context) {
        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(context);
        if (Application.isServiceRunning(ClientNsdService.class)) {
            broadcastManager.sendBroadcast(new Intent(ClientNsdService.ACTION_STOP));
        }
//        if (Application.isServiceRunning(ServerNsdService.class)) {
//            broadcastManager.sendBroadcast(new Intent(ServerNsdService.ACTION_STOP));
//        }
    }
}
