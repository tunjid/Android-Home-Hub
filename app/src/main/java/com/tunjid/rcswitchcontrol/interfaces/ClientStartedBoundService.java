package com.tunjid.rcswitchcontrol.interfaces;

import android.content.Intent;

/**
 * An interface hosting callbacks for {@link android.app.Service services} that are started,
 * then bound to a UI element. Upon leaving the UI, a notification is displayed to the user.
 * <p>
 * Created by tj.dahunsi on 3/26/17.
 */

public interface ClientStartedBoundService {
    void initialize(Intent intent);

    void onAppBackground();

    void onAppForeGround();

    boolean isConnected();

}
