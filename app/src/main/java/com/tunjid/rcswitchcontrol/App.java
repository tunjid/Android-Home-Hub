package com.tunjid.rcswitchcontrol;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.Service;
import android.content.Context;
import android.util.Log;

import com.google.android.things.pio.PeripheralManager;

import java.util.List;

/**
 * App Singleton.
 * <p>
 * Created by tj.dahunsi on 3/25/17.
 */

public class App extends android.app.Application {

    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static App getInstance() {
        return instance;
    }

    public static boolean isServiceRunning(Class<? extends Service> service) {
        final ActivityManager activityManager = (ActivityManager) instance.getSystemService(Context.ACTIVITY_SERVICE);
        final List<RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);

        for (RunningServiceInfo info : services) {
            if (info.service.getClassName().equals(service.getName())) return true;
        }
        return false;
    }

    public static boolean isAndroidThings() {
        try { PeripheralManager.getInstance(); }
        catch (NoClassDefFoundError e) { return false; } // Thrown on non Android things devices
        return true;
    }

    public static void catcher(String tag, String log, Catchable runnable) {
        try { runnable.run(); }
        catch (Exception e) { Log.e(tag, log, e); }
    }

    @FunctionalInterface
    public interface Catchable {

        void run() throws Exception;
    }
}
