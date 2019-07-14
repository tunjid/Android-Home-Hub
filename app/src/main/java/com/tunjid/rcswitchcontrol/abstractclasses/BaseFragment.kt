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

package com.tunjid.rcswitchcontrol.abstractclasses


import android.util.Log
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getDrawable
import com.google.android.material.snackbar.Snackbar
import com.tunjid.UiState
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

    private val hostingActivity: MainActivity
        get() = requireActivity() as MainActivity

    protected open val fabIconRes: Int
        @DrawableRes get() = 0

    protected open val fabTextRes: Int
        @StringRes get() = 0

    protected open val navBarColor: Int
        @ColorInt get() = ContextCompat.getColor(requireContext(), R.color.transparent)

    open val toolBarMenuRes: Int
        @MenuRes get() = 0

    open val altToolBarRes: Int
        @MenuRes get() = 0

    protected open val showsFab: Boolean
        get() = false

    open val showsAltToolBar: Boolean
        get() = false

    open val showsToolBar: Boolean
        get() = true

    open val insetFlags: InsetFlags
        get() = InsetFlags.ALL

    open val toolbarText: CharSequence
        get() = getText(R.string.app_name)

    open val altToolbarText: CharSequence
        get() = ""

    protected open val fabClickListener: View.OnClickListener
        get() = View.OnClickListener { }

    override fun onStop() {
        disposables.clear()
        super.onStop()
    }

    override fun onDestroyView() {
        disposables.clear()
        super.onDestroyView()
    }

    protected fun setFabExtended(extended: Boolean) = hostingActivity.setFabExtended(extended)

    fun onInconsistentList(exception: Exception) {
        hostingActivity.recreate()
        Log.i("RecyclerView", "Inconsistency in ${javaClass.simpleName}", exception)
    }

    open fun togglePersistentUi() {
        hostingActivity.update(fromThis())
        if (!restoredFromBackStack()) setFabExtended(true)
    }

    protected fun showSnackBar(consumer: (snackbar: Snackbar) -> Unit) =
            hostingActivity.showSnackBar(consumer)

    private fun fromThis(): UiState {
        return UiState(
                this.fabIconRes,
                this.fabTextRes,
                this.toolBarMenuRes,
                this.altToolBarRes,
                this.navBarColor,
                this.showsFab,
                this.showsToolBar,
                this.showsAltToolBar,
                this.insetFlags,
                this.toolbarText,
                this.altToolbarText,
                if (view == null) null else fabClickListener
        )
    }

}
