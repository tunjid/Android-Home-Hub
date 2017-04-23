package com.tunjid.rcswitchcontrol.abstractclasses;

import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;

import com.tunjid.rcswitchcontrol.FragmentStateManager;
import com.tunjid.rcswitchcontrol.R;

/**
 * Base Activity class
 */
public abstract class BaseActivity extends AppCompatActivity {

    private FragmentStateManager fragmentStateManager;
    protected Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fragmentStateManager = new FragmentStateManager(getSupportFragmentManager());
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

    /**
     * Starts a fragment transaction corresponding to the specified tag.
     *
     * @return true if the a fragment provided will be shown, false otherwise.
     */
    public boolean showFragment(BaseFragment fragment) {
        return fragmentStateManager.showFragment(fragment);
    }

    public Toolbar getToolbar() {
        return toolbar;
    }
}
