package com.tunjid.rcswitchcontrol.abstractclasses


import android.util.Log
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat.getDrawable
import com.google.android.material.snackbar.Snackbar
import com.tunjid.androidbootstrap.material.animator.FabExtensionAnimator
import com.tunjid.androidbootstrap.view.util.InsetFlags
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.activities.MainActivity
import io.reactivex.disposables.CompositeDisposable
import java.lang.Exception

/**
 * Base fragment
 */
abstract class BaseFragment : com.tunjid.androidbootstrap.core.abstractclasses.BaseFragment() {

    protected var disposables = CompositeDisposable()

    protected val toolBar: Toolbar
        get() = hostingActivity.toolbar

    protected open val fabState: FabExtensionAnimator.GlyphState
        get() = FabExtensionAnimator.newState(getText(R.string.app_name), getDrawable(requireContext(), R.drawable.ic_connect_24dp))

    protected open val fabClickListener: View.OnClickListener
        get() = View.OnClickListener { }

    private val hostingActivity: MainActivity
        get() = requireActivity() as MainActivity

    override fun onResume() {
        super.onResume()

        hostingActivity.toggleToolbar(showsToolBar())
    }

    override fun onStop() {
        disposables.clear()
        super.onStop()
    }

    override fun onDestroyView() {
        disposables.clear()
        super.onDestroyView()
    }

    protected fun toggleFab(show: Boolean) = hostingActivity.toggleFab(show)

    protected fun toggleToolbar(show: Boolean) = hostingActivity.toggleToolbar(show)

    protected open fun showsFab(): Boolean = false

    protected open fun showsToolBar(): Boolean = true

    fun insetFlags(): InsetFlags = InsetFlags.ALL

    protected fun setFabExtended(extended: Boolean) = hostingActivity.setFabExtended(extended)

    fun onInconsistentList(exception: Exception) {
        hostingActivity.recreate()
        Log.i("RecyclerView", "Inconsistency in ${javaClass.simpleName}", exception)
    }

    fun togglePersistentUi() {
        toggleFab(showsFab())
        toggleToolbar(showsToolBar())
        if (!restoredFromBackStack()) setFabExtended(true)

        val hostingActivity = hostingActivity
        hostingActivity.updateFab(fabState)
        hostingActivity.setFabClickListener(fabClickListener)
    }

    protected fun showSnackBar(consumer: (snackbar: Snackbar) -> Unit) =
            hostingActivity.showSnackBar(consumer)

}
