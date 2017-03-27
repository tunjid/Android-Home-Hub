package com.tunjid.rcswitchcontrol.interfaces;

import android.content.Intent;

/**
 * An interface hosing callbacks for {@link android.app.Service services} that are started,
 * and bound
 * <p>
 * Created by tj.dahunsi on 3/26/17.
 */

public interface StartedBoundService {
    void initialize(Intent intent);

    boolean isConnected();

    void onAppBackground();

    void onAppForeGround();
}
