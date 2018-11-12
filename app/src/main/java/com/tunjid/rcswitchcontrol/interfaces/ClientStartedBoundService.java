package com.tunjid.rcswitchcontrol.interfaces;

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;

import com.tunjid.rcswitchcontrol.App;

import androidx.annotation.StringRes;

import static android.content.Context.NOTIFICATION_SERVICE;

/**
 * An interface hosting callbacks for {@link android.app.Service services} that are started,
 * then bound to a UI element. Upon leaving the UI, a notification is displayed to the user.
 * <p>
 * Created by tj.dahunsi on 3/26/17.
 */

public interface ClientStartedBoundService {

    String NOTIFICATION_TYPE = "RC_SWITCH_SERVICE";

    void initialize(Intent intent);

    void onAppBackground();

    void onAppForeGround();

    boolean isConnected();

    @TargetApi(Build.VERSION_CODES.O)
    default void addChannel(@StringRes int name, @StringRes int description) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return;

        App app = App.getInstance();
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_TYPE, app.getString(name), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(app.getString(description));

        NotificationManager manager = (NotificationManager) app.getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }
}
