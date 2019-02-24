package com.tunjid.rcswitchcontrol.activities;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.View;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.tunjid.androidbootstrap.core.abstractclasses.BaseActivity;
import com.tunjid.androidbootstrap.functions.Consumer;
import com.tunjid.androidbootstrap.material.animator.FabExtensionAnimator;
import com.tunjid.androidbootstrap.view.animator.ViewHider;
import com.tunjid.androidbootstrap.view.util.InsetFlags;
import com.tunjid.androidbootstrap.view.util.ViewUtil;
import com.tunjid.rcswitchcontrol.App;
import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment;
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster;
import com.tunjid.rcswitchcontrol.fragments.ClientBleFragment;
import com.tunjid.rcswitchcontrol.fragments.ClientNsdFragment;
import com.tunjid.rcswitchcontrol.fragments.StartFragment;
import com.tunjid.rcswitchcontrol.fragments.ThingsFragment;
import com.tunjid.rcswitchcontrol.model.RcSwitch;
import com.tunjid.rcswitchcontrol.services.ClientBleService;
import com.tunjid.rcswitchcontrol.services.ClientNsdService;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import static androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener;
import static com.tunjid.androidbootstrap.view.util.ViewUtil.getLayoutParams;
import static com.tunjid.rcswitchcontrol.services.ClientBleService.BLUETOOTH_DEVICE;

public class MainActivity extends BaseActivity {

    public static final int ANIMATION_DURATION = 300;

    public static int topInset;

    private boolean insetsApplied;
    private int leftInset;
    private int rightInset;
    private int bottomInset;

    private ViewHider fabHider;
    private ViewHider toolbarHider;
    private FabExtensionAnimator fabExtensionAnimator;

    private View topInsetView;
    private View bottomInsetView;
    private View keyboardPadding;

    private Toolbar toolbar;
    private MaterialButton fab;
    private ConstraintLayout constraintLayout;
    private CoordinatorLayout coordinatorLayout;

    final FragmentManager.FragmentLifecycleCallbacks fragmentViewCreatedCallback = new FragmentManager.FragmentLifecycleCallbacks() {

        @Override
        public void onFragmentPreAttached(@NonNull FragmentManager fm, @NonNull Fragment f, @NonNull Context context) {
            adjustInsetForFragment(f); // Called when showing a fragment the first time only
        }

        @Override
        public void onFragmentViewCreated(@NonNull FragmentManager fm,
                                          @NonNull androidx.fragment.app.Fragment f,
                                          @NonNull View v,
                                          @Nullable Bundle savedInstanceState) {
            if (isNotInMainFragmentContainer(v)) return;

            BaseFragment fragment = (BaseFragment) f;
            if (fragment.restoredFromBackStack()) adjustInsetForFragment(f);

            fragment.togglePersistentUi();
            setOnApplyWindowInsetsListener(v, (view, insets) -> consumeFragmentInsets(insets));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.transparent));
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(fragmentViewCreatedCallback, false);
        setContentView(R.layout.activity_main);

        SharedPreferences preferences = getSharedPreferences(RcSwitch.SWITCH_PREFS, MODE_PRIVATE);

        String lastConnectedDevice = preferences.getString(ClientBleService.LAST_PAIRED_DEVICE, "");
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        Intent startIntent = getIntent();

        // Retrieve device from notification intent or shared preferences
        BluetoothDevice device = startIntent.hasExtra(BLUETOOTH_DEVICE)
                ? (BluetoothDevice) startIntent.getParcelableExtra(BLUETOOTH_DEVICE)
                : !TextUtils.isEmpty(lastConnectedDevice) && bluetoothAdapter != null && bluetoothAdapter.isEnabled()
                ? bluetoothAdapter.getRemoteDevice(lastConnectedDevice)
                : null;

        boolean isSavedInstance = savedInstanceState != null;
        boolean isNullDevice = device == null;
        boolean isNsdClient = startIntent.hasExtra(ClientNsdService.NSD_SERVICE_INFO_KEY)
                || !TextUtils.isEmpty(preferences.getString(ClientNsdService.LAST_CONNECTED_SERVICE, ""));

        if (!isNullDevice) startService(new Intent(this, ClientBleService.class)
                .putExtra(BLUETOOTH_DEVICE, device));
        if (isNsdClient) Broadcaster.push(new Intent(ClientNsdService.ACTION_START_NSD_DISCOVERY));

        if (!isSavedInstance) showFragment(App.isAndroidThings()
                ? ThingsFragment.newInstance()
                : isNsdClient
                ? ClientNsdFragment.newInstance()
                : isNullDevice
                ? StartFragment.newInstance()
                : ClientBleFragment.newInstance(device));
    }

    @Override public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);

        fab = findViewById(R.id.fab);
        toolbar = findViewById(R.id.toolbar);
        topInsetView = findViewById(R.id.top_inset);
        bottomInsetView = findViewById(R.id.bottom_inset);
        keyboardPadding = findViewById(R.id.keyboard_padding);
        constraintLayout = findViewById(R.id.constraint_layout);
        coordinatorLayout = findViewById(R.id.coordinator_layout);

        toolbarHider = ViewHider.of(toolbar).setDirection(ViewHider.TOP).build();
        fabHider = ViewHider.of(fab).setDirection(ViewHider.BOTTOM).build();
        fabExtensionAnimator = new FabExtensionAnimator(fab);
        fabExtensionAnimator.setExtended(true);

        setSupportActionBar(this.toolbar);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setOnApplyWindowInsetsListener(this.constraintLayout, (view, insets) -> consumeSystemInsets(insets));
    }

    public Toolbar getToolbar() {
        return toolbar;
    }

    public void toggleToolbar(boolean show) {
        if (show) toolbarHider.show();
        else toolbarHider.hide();
    }

    public void toggleFab(boolean show) {
        if (show) this.fabHider.show();
        else this.fabHider.hide();
    }

    public void setFabExtended(boolean extended) {
        fabExtensionAnimator.setExtended(extended);
    }

    public void updateFab(FabExtensionAnimator.GlyphState glyphState) {
        if (this.fabExtensionAnimator != null) this.fabExtensionAnimator.updateGlyphs(glyphState);
    }

    public void setFabClickListener(View.OnClickListener onClickListener) {
        fab.setOnClickListener(onClickListener);
    }

    public void showSnackBar(Consumer<Snackbar> consumer) {
        Snackbar snackbar = Snackbar.make(coordinatorLayout, "", Snackbar.LENGTH_SHORT);

        // Necessary to remove snackBar padding for keyboard on older versions of Android
        ViewCompat.setOnApplyWindowInsetsListener(snackbar.getView(), (view, insets) -> insets);
        consumer.accept(snackbar);
        snackbar.show();
    }

    private boolean isNotInMainFragmentContainer(View view) {
        View parent = (View) view.getParent();
        return parent == null || parent.getId() != R.id.main_fragment_container;
    }

    private WindowInsetsCompat consumeSystemInsets(WindowInsetsCompat insets) {
        if (this.insetsApplied) return insets;

        topInset = insets.getSystemWindowInsetTop();
        leftInset = insets.getSystemWindowInsetLeft();
        rightInset = insets.getSystemWindowInsetRight();
        bottomInset = insets.getSystemWindowInsetBottom();

        ViewUtil.getLayoutParams(this.topInsetView).height = topInset;
        ViewUtil.getLayoutParams(this.bottomInsetView).height = bottomInset;

        adjustInsetForFragment(getCurrentFragment());

        this.insetsApplied = true;
        return insets;
    }

    private WindowInsetsCompat consumeFragmentInsets(WindowInsetsCompat insets) {
        getLayoutParams(keyboardPadding).height = insets.getSystemWindowInsetBottom() - bottomInset;
        return insets;
    }

    private void adjustInsetForFragment(Fragment fragment) {
        if (!(fragment instanceof BaseFragment)) {return;}

        InsetFlags insetFlags = ((BaseFragment) fragment).insetFlags();
        ViewUtil.getLayoutParams(toolbar).topMargin = insetFlags.hasTopInset() ? 0 : topInset;
        TransitionManager.beginDelayedTransition(constraintLayout, new AutoTransition()
                .addTarget(R.id.main_fragment_container)
                .setDuration(ANIMATION_DURATION)
        );

        topInsetView.setVisibility(insetFlags.hasTopInset() ? View.VISIBLE : View.GONE);
        constraintLayout.setPadding(insetFlags.hasLeftInset() ? this.leftInset : 0, 0, insetFlags.hasRightInset() ? this.rightInset : 0, 0);
    }
}
