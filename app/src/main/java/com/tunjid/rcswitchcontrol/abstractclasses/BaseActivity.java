package com.tunjid.rcswitchcontrol.abstractclasses;

import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.LayoutRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;

import com.tunjid.rcswitchcontrol.R;

import java.util.ArrayList;

/**
 * Base Activity class
 */
public abstract class BaseActivity extends AppCompatActivity
        implements FragmentManager.OnBackStackChangedListener {

    private String currentFragment;
    protected Toolbar toolbar;

    @Override
    public void onCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        getSupportFragmentManager().addOnBackStackChangedListener(this);
    }

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        super.setContentView(layoutResID);

        // Check if this activity has a main fragment container viewgroup
        View mainFragmentContainer = findViewById(R.id.main_fragment_container);

        if (mainFragmentContainer == null || !(mainFragmentContainer instanceof ViewGroup)) {
            throw new IllegalArgumentException("This activity must include a ViewGroup " +
                    "with id 'main_fragment_container' for dynamically added fragments");
        }
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() <= 1) finish();
        else getSupportFragmentManager().popBackStack();
    }

    @Override
    public void onBackStackChanged() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_fragment_container);

        if (fragment != null) {
            if (!(fragment instanceof BaseFragment)) {
                throw new IllegalArgumentException("Only " + BaseFragment.class.getName()
                        + " may be added to the backstack of this activity");
            }
            currentFragment = ((BaseFragment) fragment).getStableTag();
        }
    }

    /**
     * Starts a fragment transaction corresponding to the specified tag.
     *
     * @return true if the a fragment provided will be shown, false otherwise.
     */
    public boolean showFragment(BaseFragment fragment) {

        FragmentManager fragmentManager = getSupportFragmentManager();

        ArrayList<String> fragmentTags = new ArrayList<>();

        if (fragmentManager.getFragments() != null) {
            for (Fragment f : fragmentManager.getFragments()) {
                if (f != null && f.getTag() != null) {
                    fragmentTags.add(f.getTag());
                }
            }
        }

        boolean fragmentShown = false;
        String fragmentTag = fragment.getStableTag();

        if (currentFragment == null || !currentFragment.equals(fragmentTag)) {

            boolean fragmentAlreadyExists = fragmentTags.contains(fragmentTag);

            fragmentShown = !fragmentAlreadyExists;

            Fragment fragmentToShow = fragmentAlreadyExists
                    ? fragmentManager.findFragmentByTag(fragmentTag)
                    : fragment;

            getSupportFragmentManager().beginTransaction()
                    .addToBackStack(fragmentTag)
                    .replace(R.id.main_fragment_container, fragmentToShow, fragmentTag)
                    .commit();

        }

        return fragmentShown;
    }

    public Toolbar getToolbar() {
        return toolbar;
    }

}
