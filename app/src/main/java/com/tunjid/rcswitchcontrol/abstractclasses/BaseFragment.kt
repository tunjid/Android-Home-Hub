package com.tunjid.rcswitchcontrol.abstractclasses


import android.view.View

import com.google.android.material.snackbar.Snackbar
import com.tunjid.androidbootstrap.functions.Consumer
import com.tunjid.androidbootstrap.material.animator.FabExtensionAnimator
import com.tunjid.androidbootstrap.view.util.InsetFlags
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.activities.MainActivity

import androidx.appcompat.widget.Toolbar
import io.reactivex.disposables.CompositeDisposable

import androidx.core.content.ContextCompat.getDrawable

/**
 * Base fragment
 */
abstract class BaseFragment : com.tunjid.androidbootstrap.core.abstractclasses.BaseFragment() {

    protected var disposables = CompositeDisposable()

    protected val toolBar: Toolbar
        get() = hostingActivity.toolbar

    protected val fabState: FabExtensionAnimator.GlyphState
        get() = FabExtensionAnimator.newState(getText(R.string.app_name), getDrawable(requireContext(), R.drawable.ic_connect_24dp))

    protected val fabClickListener: View.OnClickListener
        get() = View.OnClickListener { }

    private val hostingActivity: MainActivity
        get() = requireActivity() as MainActivity

    override fun onResume() {
        super.onResume()

        hostingActivity.toggleToolbar(showsToolBar())
    }

    override fun onDestroyView() {
        disposables.clear()
        super.onDestroyView()
    }

    protected fun toggleFab(show: Boolean) = hostingActivity.toggleFab(show)

    protected fun toggleToolbar(show: Boolean) = hostingActivity.toggleToolbar(show)

    protected open fun showsFab(): Boolean = false

    protected fun showsToolBar(): Boolean = true

    fun insetFlags(): InsetFlags = InsetFlags.ALL

    protected fun setFabExtended(extended: Boolean) = hostingActivity.setFabExtended(extended)

    fun togglePersistentUi() {
        toggleFab(showsFab())
        toggleToolbar(showsToolBar())
        if (!restoredFromBackStack()) setFabExtended(true)

        val hostingActivity = hostingActivity
        hostingActivity.updateFab(fabState)
        hostingActivity.setFabClickListener(fabClickListener)
    }

    protected fun showSnackBar(consumer: Consumer<Snackbar>) =
            hostingActivity.showSnackBar(consumer)

}
