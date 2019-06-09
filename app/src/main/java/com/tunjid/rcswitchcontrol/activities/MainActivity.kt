package com.tunjid.rcswitchcontrol.activities

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.tunjid.androidbootstrap.core.abstractclasses.BaseActivity
import com.tunjid.androidbootstrap.material.animator.FabExtensionAnimator
import com.tunjid.androidbootstrap.view.animator.ViewHider
import com.tunjid.androidbootstrap.view.util.ViewUtil
import com.tunjid.androidbootstrap.view.util.ViewUtil.getLayoutParams
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster
import com.tunjid.rcswitchcontrol.fragments.ClientBleFragment
import com.tunjid.rcswitchcontrol.fragments.ClientNsdFragment
import com.tunjid.rcswitchcontrol.fragments.StartFragment
import com.tunjid.rcswitchcontrol.fragments.ThingsFragment
import com.tunjid.rcswitchcontrol.model.RcSwitch
import com.tunjid.rcswitchcontrol.services.ClientBleService
import com.tunjid.rcswitchcontrol.services.ClientBleService.Companion.BLUETOOTH_DEVICE
import com.tunjid.rcswitchcontrol.services.ClientNsdService

class MainActivity : BaseActivity() {

    private var insetsApplied: Boolean = false
    private var leftInset: Int = 0
    private var rightInset: Int = 0
    private var bottomInset: Int = 0

    private lateinit var fabHider: ViewHider
    private lateinit var toolbarHider: ViewHider
    private lateinit var fabExtensionAnimator: FabExtensionAnimator

    private lateinit var topInsetView: View
    private lateinit var bottomInsetView: View
    private lateinit var keyboardPadding: View

    lateinit var toolbar: Toolbar
        private set

    private lateinit var fab: MaterialButton
    private lateinit var constraintLayout: ConstraintLayout
    private lateinit var coordinatorLayout: CoordinatorLayout

    private val fragmentViewCreatedCallback: FragmentManager.FragmentLifecycleCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {

        override fun onFragmentPreAttached(fm: FragmentManager, f: Fragment, context: Context) =
                adjustInsetForFragment(f) // Called when showing a fragment the first time only

        override fun onFragmentViewCreated(fm: FragmentManager,
                                           f: Fragment,
                                           v: View,
                                           savedInstanceState: Bundle?) {
            if (isNotInMainFragmentContainer(v)) return

            val fragment = f as BaseFragment
            if (fragment.restoredFromBackStack()) adjustInsetForFragment(f)

            fragment.togglePersistentUi()
            setOnApplyWindowInsetsListener(v) { _, insets -> consumeFragmentInsets(insets) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ContextCompat.getColor(this, R.color.transparent)
        supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentViewCreatedCallback, false)
        setContentView(R.layout.activity_main)

        val preferences = getSharedPreferences(RcSwitch.SWITCH_PREFS, Context.MODE_PRIVATE)

        val lastConnectedDevice = preferences.getString(ClientBleService.LAST_PAIRED_DEVICE, "")
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val startIntent = intent

        // Retrieve device from notification intent or shared preferences
        val device = when {
            startIntent.hasExtra(BLUETOOTH_DEVICE) -> startIntent.getParcelableExtra(BLUETOOTH_DEVICE)
            !TextUtils.isEmpty(lastConnectedDevice) && bluetoothAdapter != null && bluetoothAdapter.isEnabled -> bluetoothAdapter.getRemoteDevice(lastConnectedDevice)
            else -> null
        }

        val isSavedInstance = savedInstanceState != null
        val isNsdClient = startIntent.hasExtra(ClientNsdService.NSD_SERVICE_INFO_KEY) || !TextUtils.isEmpty(preferences.getString(ClientNsdService.LAST_CONNECTED_SERVICE, ""))

        if (device != null) startService(Intent(this, ClientBleService::class.java)
                .putExtra(BLUETOOTH_DEVICE, device))

        if (isNsdClient) Broadcaster.push(Intent(ClientNsdService.ACTION_START_NSD_DISCOVERY))

        if (!isSavedInstance) showFragment(when {
            App.isAndroidThings -> ThingsFragment.newInstance(device)
            isNsdClient -> ClientNsdFragment.newInstance()
            device == null -> StartFragment.newInstance()
            else -> ClientBleFragment.newInstance(device)
        })
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)

        fab = findViewById(R.id.fab)
        toolbar = findViewById(R.id.toolbar)
        topInsetView = findViewById(R.id.top_inset)
        bottomInsetView = findViewById(R.id.bottom_inset)
        keyboardPadding = findViewById(R.id.keyboard_padding)
        constraintLayout = findViewById(R.id.constraint_layout)
        coordinatorLayout = findViewById(R.id.coordinator_layout)

        toolbarHider = ViewHider.of(toolbar).setDirection(ViewHider.TOP).build()
        fabHider = ViewHider.of(fab).setDirection(ViewHider.BOTTOM).build()
        fabExtensionAnimator = FabExtensionAnimator(fab)
        fabExtensionAnimator.isExtended = true

        setSupportActionBar(this.toolbar)

        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        setOnApplyWindowInsetsListener(this.constraintLayout) { _, insets -> consumeSystemInsets(insets) }
    }

    fun toggleToolbar(show: Boolean) =
            if (show) toolbarHider.show()
            else toolbarHider.hide()

    fun toggleFab(show: Boolean) =
            if (show) this.fabHider.show()
            else this.fabHider.hide()

    fun setFabExtended(extended: Boolean) {
        fabExtensionAnimator.isExtended = extended
    }

    fun updateFab(glyphState: FabExtensionAnimator.GlyphState) =
            this.fabExtensionAnimator.updateGlyphs(glyphState)

    fun setFabClickListener(onClickListener: View.OnClickListener) =
            fab.setOnClickListener(onClickListener)

    fun showSnackBar(consumer: (snackbar: Snackbar) -> Unit) {
        val snackbar = Snackbar.make(coordinatorLayout, "", Snackbar.LENGTH_SHORT)

        // Necessary to remove snackBar padding for keyboard on older versions of Android
        setOnApplyWindowInsetsListener(snackbar.view) { _, insets -> insets }
        consumer.invoke(snackbar)
        snackbar.show()
    }

    private fun isNotInMainFragmentContainer(view: View): Boolean {
        val parent = view.parent as? View
        return parent == null || parent.id != R.id.main_fragment_container
    }

    private fun consumeSystemInsets(insets: WindowInsetsCompat): WindowInsetsCompat {
        if (this.insetsApplied) return insets

        topInset = insets.systemWindowInsetTop
        leftInset = insets.systemWindowInsetLeft
        rightInset = insets.systemWindowInsetRight
        bottomInset = insets.systemWindowInsetBottom

        ViewUtil.getLayoutParams(this.topInsetView).height = topInset
        ViewUtil.getLayoutParams(this.bottomInsetView).height = bottomInset

        adjustInsetForFragment(currentFragment)

        this.insetsApplied = true
        return insets
    }

    private fun consumeFragmentInsets(insets: WindowInsetsCompat): WindowInsetsCompat {
        getLayoutParams(keyboardPadding).height = insets.systemWindowInsetBottom - bottomInset
        return insets
    }

    private fun adjustInsetForFragment(fragment: Fragment) {
        if (fragment !is BaseFragment) {
            return
        }

        val insetFlags = fragment.insetFlags()
        ViewUtil.getLayoutParams(toolbar).topMargin = if (insetFlags.hasTopInset()) 0 else topInset
        TransitionManager.beginDelayedTransition(constraintLayout, AutoTransition()
                .addTarget(R.id.main_fragment_container)
                .setDuration(ANIMATION_DURATION.toLong())
        )

        topInsetView.visibility = if (insetFlags.hasTopInset()) View.VISIBLE else View.GONE
        constraintLayout.setPadding(if (insetFlags.hasLeftInset()) this.leftInset else 0, 0, if (insetFlags.hasRightInset()) this.rightInset else 0, 0)
    }

    companion object {

        const val ANIMATION_DURATION = 300

        var topInset: Int = 0
    }
}
