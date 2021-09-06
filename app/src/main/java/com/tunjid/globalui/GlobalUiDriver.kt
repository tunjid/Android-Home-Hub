package com.tunjid.globalui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.tunjid.androidx.core.content.colorAt
import com.tunjid.androidx.core.content.drawableAt
import com.tunjid.androidx.material.animator.FabExtensionAnimator
import com.tunjid.androidx.view.animator.ViewHider
import com.tunjid.androidx.view.util.PaddingProperty
import com.tunjid.androidx.view.util.innermostFocusedChild
import com.tunjid.androidx.view.util.spring
import com.tunjid.androidx.view.util.viewDelegate
import com.tunjid.androidx.view.util.withOneShotEndListener
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.databinding.ActivityMainBinding
import com.tunjid.rcswitchcontrol.di.dagger
import com.tunjid.rcswitchcontrol.di.nav
import com.tunjid.rcswitchcontrol.navigation.updatePartial
import com.tunjid.rcswitchcontrol.utils.onMenuItemClicked
import com.tunjid.rcswitchcontrol.utils.updatePartial
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * An interface for classes that host a [UiState], usually a [FragmentActivity].
 * Implementations should delegate to an instance of [GlobalUiDriver]
 */

interface GlobalUiHost {
    val globalUiController: GlobalUiController
}

interface GlobalUiController {
    var uiState: UiState
    val liveUiState: StateFlow<UiState>
}

/**
 * Drives global UI that is common from screen to screen described by a [UiState].
 * This makes it so that these persistent UI elements aren't duplicated, and only animate themselves when they change.
 * This is the default implementation of [GlobalUiController] that other implementations of
 * the same interface should delegate to.
 */
class GlobalUiDriver(
    private val host: FragmentActivity,
    private val binding: ActivityMainBinding,
) : GlobalUiController {

    private val snackbar = Snackbar.make(binding.contentRoot, "", Snackbar.LENGTH_SHORT).apply {
        view.setOnApplyWindowInsetsListener(noOpInsetsListener)
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            ::uiState.updatePartial {
                copy(systemUI = systemUI.updateSnackbarHeight(view.height))
            }
        }
        addCallback(object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                when (event) {
                    BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_SWIPE,
                    BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_ACTION,
                    BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_TIMEOUT,
                    BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_MANUAL -> ::uiState.updatePartial {
                        copy(snackbarText = "", systemUI = systemUI.updateSnackbarHeight(0))
                    }
                    BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_CONSECUTIVE -> Unit
                }
            }
        })
    }

    private val uiSizes = UISizes(host)
    private val fabExtensionAnimator = FabExtensionAnimator(binding.fab)
    private val toolbarHider = ViewHider.of(binding.toolbar).setDirection(ViewHider.TOP).build()
    private val noOpInsetsListener = View.OnApplyWindowInsetsListener { _, insets -> insets }
    private val rootInsetsListener = View.OnApplyWindowInsetsListener { _, insets ->
//        liveUiState.value = uiState.reduceSystemInsets(insets, uiSizes.navBarHeightThreshold)
        // Consume insets so other views will not see them.
        insets.consumeSystemWindowInsets()
    }
    override val liveUiState = MutableStateFlow(UiState())

    override var uiState: UiState
        get() = liveUiState.value
        set(value) {
            val updated = value.copy(
                systemUI = value.systemUI.filterNoOp(uiState.systemUI),
            )
            liveUiState.value = updated
//            liveUiState.value = updated.copy(toolbarInvalidated = false) // Reset after firing once
        }

    init {
        host.window.decorView.systemUiVisibility = FULL_CONTROL_SYSTEM_UI_FLAGS
        host.window.navigationBarColor = host.colorAt(R.color.transparent)
        host.window.statusBarColor = host.colorAt(R.color.transparent)

        binding.root.setOnApplyWindowInsetsListener(rootInsetsListener)

        binding.toolbar.setNavigationOnClickListener { host.dagger::nav.updatePartial { pop() } }
        binding.toolbar.setOnApplyWindowInsetsListener(noOpInsetsListener)

        binding.bottomNavigation.doOnLayout { updateBottomNav(this@GlobalUiDriver.uiState.bottomNavPositionalState) }
        binding.bottomNavigation.setOnApplyWindowInsetsListener(noOpInsetsListener)

        binding.contentContainer.setOnApplyWindowInsetsListener(noOpInsetsListener)
        binding.contentContainer.spring(PaddingProperty.BOTTOM).apply {
            // Scroll to text that has focus
            addEndListener { _, _, _, _ ->
                (binding.contentContainer.innermostFocusedChild as? EditText)?.let {
                    it.text = it.text
                }
            }
        }

        UiState::toolbarShows.distinct onChanged {
            println("Toolbar shows: $it")
            toolbarHider.set(true)
//            toolbarHider::set
        }
        UiState::toolbarState.distinct onChanged binding.toolbar::updatePartial
//        UiState::toolbarMenuClickListener.distinct onChanged binding.toolbar::onMenuItemClicked

        UiState::altToolbarShows.distinct onChanged ::toggleAltToolbar
        UiState::altToolbarState.distinct onChanged binding.altToolbar::updatePartial
//        UiState::altToolbarMenuClickListener.distinct onChanged binding.altToolbar::onMenuItemClicked

        UiState::toolbarPosition.distinct onChanged { y ->
            binding.toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = y }
            binding.altToolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = y }
        }

        UiState::fabGlyphs.distinct onChanged this::setFabGlyphs
        UiState::fabState.distinct onChanged this::updateFabState
        UiState::fabClickListener.distinct onChanged this::setFabClickListener
        UiState::fabExtended.distinct onChanged fabExtensionAnimator::isExtended::set
        UiState::fabTransitionOptions.distinct onChanged this::setFabTransitionOptions

        UiState::snackbarText.distinct onChanged this::showSnackBar
        UiState::navBarColor.distinct onChanged this::setNavBarColor
        UiState::lightStatusBar.distinct onChanged this::setLightStatusBar
        UiState::fragmentContainerState.distinct onChanged this::updateFragmentContainer
        UiState::backgroundColor.distinct onChanged binding.contentRoot::animateBackground

        UiState::bottomNavPositionalState.distinct onChanged this::updateBottomNav
        UiState::snackbarPositionalState.distinct onChanged this::updateSnackbar
    }

    private fun updateFabState(state: FabPositionalState) {
        if (state.fabVisible) binding.fab.isVisible = true
        val fabTranslation = when {
            state.fabVisible -> {
                val navBarHeight = state.navBarSize
                val snackbarHeight = state.snackbarHeight
                val bottomNavHeight = uiSizes.bottomNavSize countIf state.bottomNavVisible
                val insetClearance = max(bottomNavHeight, state.keyboardSize)
                val totalBottomClearance = navBarHeight + insetClearance + snackbarHeight

                -totalBottomClearance.toFloat()
            }
            else -> binding.fab.height.toFloat() + binding.fab.paddingBottom
        }

        binding.fab.softSpring(SpringAnimation.TRANSLATION_Y)
            .withOneShotEndListener {
                binding.fab.isVisible = state.fabVisible
            } // Make the fab gone if hidden
            .animateToFinalPosition(fabTranslation)
    }

    private fun updateSnackbar(state: SnackbarPositionalState) {
        snackbar.view.doOnLayout {
            val bottomNavClearance = uiSizes.bottomNavSize countIf state.bottomNavVisible
            val navBarClearance = state.navBarSize countIf state.insetDescriptor.hasBottomInset
            var insetClearance = uiSizes.snackbarPadding

            insetClearance +=
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) max(
                    bottomNavClearance,
                    state.keyboardSize
                )
                else max(bottomNavClearance + navBarClearance, state.keyboardSize)

            it.softSpring(SpringAnimation.TRANSLATION_Y)
                .animateToFinalPosition(-insetClearance.toFloat())
        }
    }

    private fun updateBottomNav(state: BottomNavPositionalState) {
        val navBarClearance = state.navBarSize countIf state.insetDescriptor.hasBottomInset
        binding.bottomNavigation.softSpring(SpringAnimation.TRANSLATION_Y)
            .animateToFinalPosition(if (state.bottomNavVisible) -navBarClearance.toFloat() else uiSizes.bottomNavSize.toFloat())
    }

    private fun updateFragmentContainer(state: FragmentContainerPositionalState) {
        val bottomNavHeight = uiSizes.bottomNavSize countIf state.bottomNavVisible
        val insetClearance = max(bottomNavHeight, state.keyboardSize)
        val navBarClearance = state.navBarSize countIf state.insetDescriptor.hasBottomInset
        val totalBottomClearance = insetClearance + navBarClearance

        val statusBarSize = state.statusBarSize countIf state.insetDescriptor.hasTopInset
        val toolbarHeight = uiSizes.toolbarSize countIf !state.toolbarOverlaps
        val topClearance = statusBarSize + toolbarHeight

        binding.contentContainer
            .softSpring(PaddingProperty.TOP)
            .animateToFinalPosition(topClearance.toFloat())

        binding.contentContainer
            .softSpring(PaddingProperty.BOTTOM)
            .animateToFinalPosition(totalBottomClearance.toFloat())
    }

    private fun toggleAltToolbar(show: Boolean) {
        if (show) toolbarHider.hide()
        else if (uiState.toolbarShows) toolbarHider.show()

        binding.altToolbar.visibility = if (show) View.VISIBLE else View.INVISIBLE
    }

    private fun setNavBarColor(color: Int) {
        binding.navBackground.background = GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(color, Color.TRANSPARENT)
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) uiFlagTweak {
            if (color.isBrightColor) it or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            else it and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
    }

    private fun setLightStatusBar(lightStatusBar: Boolean) = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> uiFlagTweak { flags ->
            if (lightStatusBar) flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            else flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
        else -> host.window.statusBarColor =
            host.colorAt(if (lightStatusBar) R.color.transparent else R.color.black_50)
    }

    private fun uiFlagTweak(tweaker: (Int) -> Int) = host.window.decorView.run {
        systemUiVisibility = tweaker(systemUiVisibility)
    }

    private fun setFabGlyphs(fabGlyphState: Pair<Int, CharSequence>) = host.runOnUiThread {
        val (@DrawableRes icon: Int, title: CharSequence) = fabGlyphState
        fabExtensionAnimator.updateGlyphs(title, if (icon != 0) host.drawableAt(icon) else null)
    }

    private fun setFabClickListener(onClickListener: ((View) -> Unit)?) =
        binding.fab.setOnClickListener(onClickListener)

    private fun setFabTransitionOptions(options: (SpringAnimation.() -> Unit)?) {
        options?.let(fabExtensionAnimator::configureSpring)
    }

    private fun showSnackBar(message: CharSequence) = if (message.isNotBlank()) {
        snackbar.setText(message)
        snackbar.show()
    } else Unit

    companion object {
        private const val FULL_CONTROL_SYSTEM_UI_FLAGS =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
    }

    /**
     * Maps slices of the ui state to the function that should be invoked when it changes
     */
    private infix fun <T> Flow<T>.onChanged(consumer: (T) -> Unit) {
        host.lifecycleScope.launch { distinctUntilChanged().collect { consumer(it) } }

    }

    private val <T : Any?> ((UiState) -> T).distinct
        get() = liveUiState
            .map { invoke(it) }
            .distinctUntilChanged()
}

private val View.backgroundAnimator by viewDelegate(ValueAnimator().apply {
    setIntValues(Color.TRANSPARENT)
    setEvaluator(ArgbEvaluator())
})

private fun View.animateBackground(@ColorInt to: Int) {
    if (backgroundAnimator.isRunning) backgroundAnimator.cancel()
    backgroundAnimator.removeAllUpdateListeners()
    backgroundAnimator.addUpdateListener { setBackgroundColor(it.animatedValue as Int) }
    backgroundAnimator.setIntValues(backgroundAnimator.animatedValue as Int, to)
    backgroundAnimator.start()
}

fun View.softSpring(property: FloatPropertyCompat<View>) =
    spring(property, SpringForce.STIFFNESS_LOW)

private val Int.isBrightColor get() = ColorUtils.calculateLuminance(this) > 0.5

private fun ViewHider<*>.set(show: Boolean) =
    if (show) show()
    else hide()

private class UISizes(host: FragmentActivity) {
    val toolbarSize: Int = host.resources.getDimensionPixelSize(R.dimen.triple_and_half_margin)
    val bottomNavSize: Int = host.resources.getDimensionPixelSize(R.dimen.triple_and_half_margin)
    val snackbarPadding: Int = host.resources.getDimensionPixelSize(R.dimen.half_margin)
    val navBarHeightThreshold: Int = host.resources.getDimensionPixelSize(R.dimen.quintuple_margin)
}

private infix fun Int.countIf(condition: Boolean) = if (condition) this else 0
