package com.tunjid.rcswitchcontrol.abstractclasses;


import android.view.View;

import com.google.android.material.snackbar.Snackbar;
import com.tunjid.androidbootstrap.functions.Consumer;
import com.tunjid.androidbootstrap.material.animator.FabExtensionAnimator;
import com.tunjid.androidbootstrap.view.util.InsetFlags;
import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.activities.MainActivity;

import androidx.appcompat.widget.Toolbar;
import io.reactivex.disposables.CompositeDisposable;

import static androidx.core.content.ContextCompat.getDrawable;

/**
 * Base fragment
 */
@SuppressWarnings("WeakerAccess")
public abstract class BaseFragment extends com.tunjid.androidbootstrap.core.abstractclasses.BaseFragment {

    protected CompositeDisposable disposables = new CompositeDisposable();

    protected Toolbar getToolBar() {
        return getHostingActivity().getToolbar();
    }

    @Override public void onResume() {
        super.onResume();

        ((MainActivity) requireActivity()).toggleToolbar(showsToolBar());
    }

    @Override public void onDestroyView() {
        disposables.clear();
        super.onDestroyView();
    }

    protected void toggleFab(boolean show) { getHostingActivity().toggleFab(show); }

    protected void toggleToolbar(boolean show) { getHostingActivity().toggleToolbar(show); }

    protected boolean showsFab() { return false; }

    protected boolean showsToolBar() { return true; }

    public InsetFlags insetFlags() { return InsetFlags.ALL; }

    protected void setFabExtended(boolean extended) {
        getHostingActivity().setFabExtended(extended);
    }

    public void togglePersistentUi() {
        toggleFab(showsFab());
        toggleToolbar(showsToolBar());
        if (!restoredFromBackStack()) setFabExtended(true);

        MainActivity hostingActivity = getHostingActivity();
        hostingActivity.updateFab(getFabState());
        hostingActivity.setFabClickListener(getFabClickListener());
    }

    protected FabExtensionAnimator.GlyphState getFabState() {
        return FabExtensionAnimator.newState(getText(R.string.app_name), getDrawable(requireContext(), R.drawable.ic_connect_24dp));
    }

    protected View.OnClickListener getFabClickListener() { return view -> {}; }

    protected void showSnackBar(Consumer<Snackbar> consumer) { getHostingActivity().showSnackBar(consumer); }

    private MainActivity getHostingActivity() { return (MainActivity) requireActivity(); }

}
