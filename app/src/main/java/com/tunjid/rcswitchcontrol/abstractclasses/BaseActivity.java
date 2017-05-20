package com.tunjid.rcswitchcontrol.abstractclasses;

import android.support.v7.widget.Toolbar;

/**
 * Base Activity class
 */
public abstract class BaseActivity extends com.tunjid.androidbootstrap.core.abstractclasses.BaseActivity {

    protected Toolbar toolbar;

    public Toolbar getToolbar() {
        return toolbar;
    }
}
