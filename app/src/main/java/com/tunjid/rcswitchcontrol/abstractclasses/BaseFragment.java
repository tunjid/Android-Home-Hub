package com.tunjid.rcswitchcontrol.abstractclasses;

import android.support.v4.app.Fragment;
import android.support.v7.widget.Toolbar;

/**
 * Base fragment
 */
public abstract class BaseFragment extends Fragment {

    public String getStableTag() {
        return getClass().getSimpleName();
    }

    public boolean showFragment(BaseFragment fragment) {
        return ((BaseActivity) getActivity()).showFragment(fragment);
    }

    public Toolbar getToolBar() {
        return ((BaseActivity) getActivity()).getToolbar();
    }
}
