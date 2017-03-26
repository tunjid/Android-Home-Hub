package com.tunjid.rcswitchcontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.tunjid.rcswitchcontrol.nsd.NsdHelper;
import com.tunjid.rcswitchcontrol.nsd.abstractclasses.DiscoveryListener;
import com.tunjid.rcswitchcontrol.nsd.abstractclasses.ResolveListener;
import com.tunjid.rcswitchcontrol.services.ClientNsdService;

import static com.tunjid.rcswitchcontrol.model.RcSwitch.SWITCH_PREFS;

public class WifiStatusReceiver extends BroadcastReceiver {

    public static final int SEARCH_LENGTH_MILLIS = 15000;

    @Override
    public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
            case WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION:
                if (intent.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false)) {
                    connectLastNsdService(context);
                }
                break;
        }
    }

    private void connectLastNsdService(final Context context) {

        final String lastConnection = context.getSharedPreferences(SWITCH_PREFS, Context.MODE_PRIVATE)
                .getString(ClientNsdService.LAST_CONNECTED_SERVICE, "");

        if (TextUtils.isEmpty(lastConnection)) return;

        final NsdHelper nsdHelper = new NsdHelper(context);

        final ResolveListener resolveListener = new ResolveListener() {
            @Override
            public void onServiceResolved(NsdServiceInfo service) {
                if (service.getServiceName().equals(lastConnection)) {
                    Intent intent = new Intent(context, ClientNsdService.class);
                    intent.putExtra(ClientNsdService.NSD_SERVICE_INFO_KEY, service);
                    context.startService(intent);

                    nsdHelper.tearDown();
                }
            }
        };

        final DiscoveryListener discoveryListener = new DiscoveryListener() {
            @Override
            public void onServiceFound(NsdServiceInfo service) {
                super.onServiceFound(service);
                nsdHelper.getNsdManager().resolveService(service, resolveListener);
            }
        };

        nsdHelper.initializeDiscoveryListener(discoveryListener);
        nsdHelper.discoverServices();

        new Handler(Looper.myLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                nsdHelper.tearDown();
            }
        }, SEARCH_LENGTH_MILLIS);
    }
}
