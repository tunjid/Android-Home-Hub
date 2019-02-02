package com.tunjid.rcswitchcontrol.abstractclasses;


import androidx.appcompat.widget.Toolbar;

/**
 * Base fragment
 */
public abstract class BaseFragment extends com.tunjid.androidbootstrap.core.abstractclasses.BaseFragment {

    protected Toolbar getToolBar() {
        return ((BaseActivity) requireActivity()).getToolbar();
    }
}
