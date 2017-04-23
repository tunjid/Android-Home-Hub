package com.tunjid.rcswitchcontrol;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment;

import java.util.HashSet;
import java.util.Set;

/**
 * A class that keeps track of the {@link Fragment fragments} in an
 * {@link android.app.Activity activity's} {@link FragmentManager}
 * <p>
 * Created by tj.dahunsi on 4/23/17.
 */

public class FragmentStateManager {

    private final FragmentManager fragmentManager;
    private final Set<String> fragmentTags;

    private String currentFragment;

    /**
     * Used to keep track of the fragments in the FragmentManager
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final FragmentManager.FragmentLifecycleCallbacks fragmentLifecycleCallbacks =
            new FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentAttached(FragmentManager fm, Fragment f, Context context) {
                    fragmentTags.add(f.getTag());
                }

                @Override
                public void onFragmentDetached(FragmentManager fm, Fragment f) {
                    fragmentTags.remove(f.getTag());
                }
            };

    /**
     * Used to keep track of the current fragment shown in the fragment manager
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final FragmentManager.OnBackStackChangedListener backStackChangedListener =
            new FragmentManager.OnBackStackChangedListener() {
                @Override
                public void onBackStackChanged() {
                    Fragment fragment = fragmentManager.findFragmentById(R.id.main_fragment_container);

                    if (fragment != null) {
                        if (!(fragment instanceof BaseFragment)) {
                            throw new IllegalArgumentException("Only " + BaseFragment.class.getName()
                                    + " may be added to the backstack of this activity");
                        }
                        currentFragment = ((BaseFragment) fragment).getStableTag();
                    }
                }
            };

    public FragmentStateManager(FragmentManager fragmentManager) {
        this.fragmentManager = fragmentManager;
        fragmentTags = new HashSet<>();

        int backStackCount = fragmentManager.getBackStackEntryCount();

        // Restore previous backstack entries in the Fragment manager
        for (int i = 0; i < backStackCount; i++) {
            fragmentTags.add(fragmentManager.getBackStackEntryAt(i).getName());
        }

        fragmentManager.registerFragmentLifecycleCallbacks(fragmentLifecycleCallbacks, false);
        fragmentManager.addOnBackStackChangedListener(backStackChangedListener);
    }

    /**
     * Starts a fragment transaction corresponding to the specified tag.
     *
     * @return true if the a fragment provided will be shown, false if the fragment instance already
     * exists and will be restored instead.
     */
    public boolean showFragment(BaseFragment fragment) {

        boolean fragmentShown = false;
        String fragmentTag = fragment.getStableTag();

        if (currentFragment == null || !currentFragment.equals(fragmentTag)) {

            boolean fragmentAlreadyExists = fragmentTags.contains(fragmentTag);

            fragmentShown = !fragmentAlreadyExists;

            Fragment fragmentToShow = fragmentAlreadyExists
                    ? fragmentManager.findFragmentByTag(fragmentTag)
                    : fragment;

            fragmentManager.beginTransaction()
                    .addToBackStack(fragmentTag)
                    .replace(R.id.main_fragment_container, fragmentToShow, fragmentTag)
                    .commit();
        }
        return fragmentShown;
    }
}
