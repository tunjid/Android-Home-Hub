package com.tunjid.rcswitchcontrol;

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
}
