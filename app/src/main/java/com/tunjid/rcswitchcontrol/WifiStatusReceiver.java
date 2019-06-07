package com.tunjid.rcswitchcontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import com.tunjid.androidbootstrap.communications.nsd.NsdHelper;
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster;
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
import static com.tunjid.rcswitchcontrol.services.ClientNsdService.LAST_CONNECTED_SERVICE;
import static com.tunjid.rcswitchcontrol.services.ServerNsdService.SERVER_FLAG;

public class WifiStatusReceiver extends BroadcastReceiver {

    public static final int SEARCH_LENGTH_MILLIS = 15000;

    private boolean isSearching;

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean flag = false;
        String action = intent.getAction();
        if (action == null) return;

        switch (action) {
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
        else stopClient();
    }

    private void connectLastNsdService(final Context context) {
        if (isSearching) return;

        SharedPreferences preferences = context.getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE);

        // If we're running the BLE service, and we were designated as a NSD server, start it.
        if (App.Companion.isServiceRunning(ClientBleService.class) && preferences.getBoolean(SERVER_FLAG, false)) {
            context.startService(new Intent(context, ServerNsdService.class));
            return;
        }

        final String lastServer = preferences.getString(LAST_CONNECTED_SERVICE, "");

        if (lastServer == null) return;

        final AtomicReference<NsdHelper> helperReference = new AtomicReference<>();

        NsdHelper nsdHelper = NsdHelper.getBuilder(context)
                .setServiceFoundConsumer(service -> {
                    NsdHelper same = helperReference.get();
                    if (same != null && lastServer.equals(service.getServiceName()))
                        same.resolveService(service);
                })
                .setResolveSuccessConsumer(service -> {
                    if (!lastServer.equals(service.getServiceName())) return;

                    context.startService(new Intent(context, ClientNsdService.class)
                            .putExtra(ClientNsdService.NSD_SERVICE_INFO_KEY, service));
                    helperReference.get().tearDown();
                    isSearching = false;
                })
                .build();

        helperReference.set(nsdHelper);
        nsdHelper.discoverServices();
        isSearching = true;

        new Handler(Looper.myLooper()).postDelayed(() -> {
            if (helperReference.get() == null) return;
            helperReference.get().tearDown();
            isSearching = false;
        }, SEARCH_LENGTH_MILLIS);
    }

    private void stopClient() {
        if (App.Companion.isServiceRunning(ClientNsdService.class)) {
            Broadcaster.Companion.push(new Intent(ClientNsdService.ACTION_STOP));
        }
//        if (App.isServiceRunning(ServerNsdService.class)) {
//            broadcastManager.sendBroadcast(new Intent(ServerNsdService.ACTION_STOP));
//        }
    }
}
