package com.tunjid.rcswitchcontrol.abstractclasses;


import androidx.appcompat.widget.Toolbar;
import io.reactivex.disposables.CompositeDisposable;

/**
 * Base fragment
 */
public abstract class BaseFragment extends com.tunjid.androidbootstrap.core.abstractclasses.BaseFragment {

    protected CompositeDisposable disposables = new CompositeDisposable();

    protected Toolbar getToolBar() {
        return ((BaseActivity) requireActivity()).getToolbar();
    }

    @Override public void onDestroyView() {
        disposables.clear();
        super.onDestroyView();
    }
}
