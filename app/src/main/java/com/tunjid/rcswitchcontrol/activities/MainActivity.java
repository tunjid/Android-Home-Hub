package com.tunjid.rcswitchcontrol.activities;

import android.os.Bundle;
import android.support.v7.widget.Toolbar;

import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.abstractclasses.BaseActivity;
import com.tunjid.rcswitchcontrol.fragments.StartFragment;

public class MainActivity extends BaseActivity {

    public static final String GO_TO_SCAN = "GO_TO_SCAN";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (savedInstanceState == null) showFragment(StartFragment.newInstance());
    }
}
