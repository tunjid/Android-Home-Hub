package com.tunjid.rcswitchcontrol.abstractclasses;

import android.support.v7.widget.Toolbar;

/**
 * Base fragment
 */
public abstract class BaseFragment extends com.tunjid.androidbootstrap.core.abstractclasses.BaseFragment {

    public Toolbar getToolBar() {
        return ((BaseActivity) getActivity()).getToolbar();
    }
}
