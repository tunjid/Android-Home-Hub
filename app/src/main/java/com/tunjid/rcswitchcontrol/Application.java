package com.tunjid.rcswitchcontrol;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;

import com.google.android.things.pio.PeripheralManagerService;

import java.util.List;

/**
 * Application Singleton.
 * <p>
 * Created by tj.dahunsi on 3/25/17.
 */

public class Application extends android.app.Application {

    private static Application instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static Application getInstance() {
        return instance;
    }

    public static boolean isServiceRunning(Class<? extends Service> serviceClass) {
        final ActivityManager activityManager = (ActivityManager) instance.getSystemService(Context.ACTIVITY_SERVICE);
        final List<ActivityManager.RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);

        for (ActivityManager.RunningServiceInfo runningServiceInfo : services) {
            if (runningServiceInfo.service.getClassName().equals(serviceClass.getName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAndroidThings() {
        try {
            new PeripheralManagerService();
            return true;
        }
        catch (NoClassDefFoundError e) { // Thrown on non Android things devices
            return false;
        }
    }
}
