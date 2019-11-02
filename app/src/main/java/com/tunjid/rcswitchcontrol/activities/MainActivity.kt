/*
 * MIT License
 *
 * Copyright (c) 2019 Adetunji Dahunsi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tunjid.rcswitchcontrol.activities

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.MenuItem
import android.view.View
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
import android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.tunjid.UiState
import com.tunjid.androidx.core.components.services.HardServiceConnection
import com.tunjid.androidx.core.content.drawableAt
import com.tunjid.androidx.material.animator.FabExtensionAnimator
import com.tunjid.androidx.navigation.Navigator
import com.tunjid.androidx.navigation.stackNavigationController
import com.tunjid.androidx.view.animator.ViewHider
import com.tunjid.androidx.view.util.marginLayoutParams
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.abstractclasses.BaseFragment
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster
import com.tunjid.rcswitchcontrol.fragments.ControlFragment
import com.tunjid.rcswitchcontrol.fragments.StartFragment
import com.tunjid.rcswitchcontrol.services.ClientNsdService
import com.tunjid.rcswitchcontrol.services.ServerNsdService
import com.tunjid.rcswitchcontrol.utils.TOOLBAR_ANIM_DELAY
import com.tunjid.rcswitchcontrol.utils.update
import com.tunjid.androidx.material.animator.FabExtensionAnimator.SimpleGlyphState as GlyphState

class MainActivity : AppCompatActivity(R.layout.activity_main), Navigator.Controller {

    private var insetsApplied: Boolean = false
    private var leftInset: Int = 0
    private var rightInset: Int = 0

    private lateinit var fabHider: ViewHider<MaterialButton>
    private lateinit var toolbarHider: ViewHider<Toolbar>
    private lateinit var fabExtensionAnimator: FabExtensionAnimator

    private lateinit var topInsetView: View
    private lateinit var bottomInsetView: View
    private lateinit var keyboardPadding: View
    private lateinit var navBackgroundView: View

    lateinit var toolbar: Toolbar
    private lateinit var altToolbar: Toolbar
    private lateinit var fab: MaterialButton
    private lateinit var constraintLayout: ConstraintLayout
    private lateinit var coordinatorLayout: CoordinatorLayout

    private lateinit var uiState: UiState

    override val navigator: Navigator by stackNavigationController(R.id.main_fragment_container)

    private val fragmentViewCreatedCallback: FragmentManager.FragmentLifecycleCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {

        override fun onFragmentPreAttached(fm: FragmentManager, f: Fragment, context: Context) =
                adjustInsetForFragment(f) // Called when showing a fragment the first time only

        override fun onFragmentViewCreated(fm: FragmentManager,
                                           f: Fragment,
                                           v: View,
                                           savedInstanceState: Bundle?) {
            if (isNotInMainFragmentContainer(v)) return

            val fragment = f as BaseFragment
            adjustInsetForFragment(f)

            fragment.togglePersistentUi()
            setOnApplyWindowInsetsListener(v) { _, insets -> consumeFragmentInsets(insets) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ContextCompat.getColor(this, R.color.transparent)
        supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentViewCreatedCallback, false)
        setContentView(R.layout.activity_main)

        uiState = if (savedInstanceState == null) UiState.freshState() else savedInstanceState.getParcelable(UI_STATE)!!

        val startIntent = intent

        val isSavedInstance = savedInstanceState != null
        val isNsdServer = ServerNsdService.isServer
        val isNsdClient = startIntent.hasExtra(ClientNsdService.NSD_SERVICE_INFO_KEY) || ClientNsdService.lastConnectedService != null

        if (isNsdServer) HardServiceConnection(applicationContext, ServerNsdService::class.java).start()
        if (isNsdClient) Broadcaster.push(Intent(ClientNsdService.ACTION_START_NSD_DISCOVERY))

        if (!isSavedInstance) navigator.push(when {
            App.isAndroidThings || isNsdClient || isNsdServer -> ControlFragment.newInstance()
            else -> StartFragment.newInstance()
        })
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)

        fab = findViewById(R.id.fab)
        toolbar = findViewById(R.id.toolbar)
        altToolbar = findViewById(R.id.alt_toolbar)
        topInsetView = findViewById(R.id.top_inset)
        bottomInsetView = findViewById(R.id.bottom_inset)
        keyboardPadding = findViewById(R.id.keyboard_padding)
        navBackgroundView = findViewById(R.id.nav_background)
        constraintLayout = findViewById(R.id.constraint_layout)
        coordinatorLayout = findViewById(R.id.coordinator_layout)

        toolbarHider = ViewHider.of(toolbar).setDirection(ViewHider.TOP).build()
        fabHider = ViewHider.of(fab).setDirection(ViewHider.BOTTOM).build()
        fabExtensionAnimator = FabExtensionAnimator(fab)
        fabExtensionAnimator.isExtended = true

        toolbar.setOnMenuItemClickListener(this::onMenuItemClicked)
        altToolbar.setOnMenuItemClickListener(this::onMenuItemClicked)

        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = DEFAULT_SYSTEM_UI_FLAGS
        setOnApplyWindowInsetsListener(this.constraintLayout) { _, insets -> consumeSystemInsets(insets) }
    }

    override fun onStart() {
        super.onStart()
        updateUI(true, uiState)
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(UI_STATE, uiState)
        super.onSaveInstanceState(outState)
    }

    override fun invalidateOptionsMenu() {
        super.invalidateOptionsMenu()
        toolbar.postDelayed(TOOLBAR_ANIM_DELAY.toLong()) {
            navigator.current?.onPrepareOptionsMenu(toolbar.menu)
        }
        navigator.current?.onPrepareOptionsMenu(altToolbar.menu)
    }

    fun update(state: UiState) = updateUI(false, state)

    private fun updateMainToolBar(menu: Int, title: CharSequence) = toolbar.update(menu, title).also {
        navigator.current?.onPrepareOptionsMenu(toolbar.menu)
    }

    private fun updateAltToolbar(menu: Int, title: CharSequence) = altToolbar.update(menu, title)

    private fun toggleAltToolbar(show: Boolean) {
        val current = navigator.current
        if (show) toggleToolbar(false)
        else if (current is BaseFragment) toggleToolbar(current.showsToolBar)

        altToolbar.visibility = if (show) View.VISIBLE else View.INVISIBLE
    }

    private fun toggleToolbar(show: Boolean) {
        if (show) toolbarHider.show()
        else toolbarHider.hide()
        altToolbar.visibility = View.INVISIBLE
    }

    private fun setNavBarColor(color: Int) {
        navBackgroundView.background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(color, Color.TRANSPARENT))
    }

    private fun setFabIcon(@DrawableRes icon: Int, @StringRes title: Int) = runOnUiThread {
        if (icon != 0 && title != 0) fabExtensionAnimator.updateGlyphs(GlyphState(
                getText(title),
                drawableAt(icon)!!))
    }

    private fun toggleFab(show: Boolean) =
            if (show) this.fabHider.show()
            else this.fabHider.hide()

    private fun setFabClickListener(onClickListener: View.OnClickListener?) =
            fab.setOnClickListener(onClickListener)

    fun setFabExtended(extended: Boolean) {
        fabExtensionAnimator.isExtended = extended
    }

    fun showSnackBar(consumer: (snackbar: Snackbar) -> Unit) {
        val snackbar = Snackbar.make(coordinatorLayout, "", Snackbar.LENGTH_SHORT)

        // Necessary to remove snackBar padding for keyboard on older versions of Android
        setOnApplyWindowInsetsListener(snackbar.view) { _, insets -> insets }
        consumer.invoke(snackbar)
        snackbar.show()
    }

    private fun onMenuItemClicked(item: MenuItem): Boolean {
        val fragment = navigator.current
        val selected = fragment != null && fragment.onOptionsItemSelected(item)

        return selected || onOptionsItemSelected(item)
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

        topInsetView.layoutParams.height = topInset
        bottomInsetView.layoutParams.height = bottomInset
        navBackgroundView.layoutParams.height = bottomInset

        adjustInsetForFragment(navigator.current)

        this.insetsApplied = true
        return insets
    }

    private fun consumeFragmentInsets(insets: WindowInsetsCompat): WindowInsetsCompat {
        keyboardPadding.layoutParams.height = insets.systemWindowInsetBottom - bottomInset
        return insets
    }

    private fun adjustInsetForFragment(fragment: Fragment?) {
        if (fragment !is BaseFragment) return

        val insetFlags = fragment.insetFlags
        toolbar.marginLayoutParams.topMargin = if (insetFlags.hasTopInset) 0 else topInset
        TransitionManager.beginDelayedTransition(constraintLayout, AutoTransition()
                .addTarget(R.id.main_fragment_container)
                .setDuration(ANIMATION_DURATION.toLong())
        )

        topInsetView.visibility = if (insetFlags.hasTopInset) View.VISIBLE else View.GONE
        bottomInsetView.visibility = if (insetFlags.hasBottomInset) View.VISIBLE else View.GONE
        constraintLayout.setPadding(if (insetFlags.hasLeftInset) this.leftInset else 0, 0, if (insetFlags.hasRightInset) this.rightInset else 0, 0)
    }

    private fun updateUI(force: Boolean, state: UiState) {
        uiState = uiState.diff(force,
                state,
                this::toggleFab,
                this::toggleToolbar,
                this::toggleAltToolbar,
                this::setNavBarColor,
                {},
                this::setFabIcon,
                this::updateMainToolBar,
                this::updateAltToolbar,
                this::setFabClickListener
        )
    }

    companion object {

        const val ANIMATION_DURATION = 300
        private const val UI_STATE = "APP_UI_STATE"

        private const val DEFAULT_SYSTEM_UI_FLAGS = (SYSTEM_UI_FLAG_LAYOUT_STABLE
                or SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        var topInset: Int = 0
        var bottomInset: Int = 0
    }
}
