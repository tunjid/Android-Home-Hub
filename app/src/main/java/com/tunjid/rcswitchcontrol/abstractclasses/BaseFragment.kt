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


import android.graphics.Color
import android.os.Build
import android.util.Log
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.tunjid.androidx.core.content.colorAt
import com.tunjid.androidx.navigation.Navigator
import com.tunjid.androidx.navigation.activityNavigatorController
import com.tunjid.androidx.view.util.InsetFlags
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.utils.AppNavigator
import com.tunjid.rcswitchcontrol.utils.GlobalUiController
import com.tunjid.rcswitchcontrol.utils.InsetProvider
import com.tunjid.rcswitchcontrol.utils.activityGlobalUiController
import io.reactivex.disposables.CompositeDisposable

/**
 * Base fragment
 */
abstract class BaseFragment(layoutRes: Int = 0) : Fragment(layoutRes),
        InsetProvider,
        GlobalUiController,
        Navigator.Controller,
        Navigator.TagProvider {

    override val stableTag: String = javaClass.simpleName

    override val insetFlags: InsetFlags get() = InsetFlags.ALL

    protected val disposables = CompositeDisposable()

    override val navigator by activityNavigatorController<AppNavigator>()

    override var uiState by activityGlobalUiController()

    override fun onStop() {
        disposables.clear()
        super.onStop()
    }

    override fun onDestroyView() {
        disposables.clear()
        super.onDestroyView()
    }

    fun onInconsistentList(exception: Exception) {
        activity?.recreate()
        Log.i("RecyclerView", "Inconsistency in ${javaClass.simpleName}", exception)
    }

    protected fun defaultUi(
            @DrawableRes fabIcon: Int = uiState.fabIcon,
            @StringRes fabText: Int = uiState.fabText,
            fabShows: Boolean = false,
            fabExtended: Boolean = uiState.fabExtended,
            @MenuRes toolBarMenu: Int = 0,
            toolbarShows: Boolean = true,
            toolbarInvalidated: Boolean = false,
            toolbarTitle: CharSequence = "",
            @MenuRes altToolBarMenu: Int = 0,
            altToolBarShows: Boolean = false,
            altToolbarInvalidated: Boolean = false,
            altToolbarTitle: CharSequence = "",
            @ColorInt navBarColor: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) requireActivity().colorAt(R.color.transparent) else Color.BLACK,
            systemUiShows: Boolean = true,
            hasLightNavBar: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
            fabClickListener: View.OnClickListener? = null
    ) = updateUi(
            fabIcon,
            fabText,
            fabShows,
            fabExtended,
            toolBarMenu,
            toolbarShows,
            toolbarInvalidated,
            toolbarTitle,
            altToolBarMenu,
            altToolBarShows,
            altToolbarInvalidated,
            altToolbarTitle,
            navBarColor,
            systemUiShows,
            hasLightNavBar,
            fabClickListener
    )


    protected fun updateUi(
            @DrawableRes fabIcon: Int = uiState.fabIcon,
            @StringRes fabText: Int = uiState.fabText,
            fabShows: Boolean = uiState.fabShows,
            fabExtended: Boolean = uiState.fabExtended,
            @MenuRes toolBarMenu: Int = uiState.toolBarMenu,
            toolbarShows: Boolean = uiState.toolbarShows,
            toolbarInvalidated: Boolean = uiState.toolbarInvalidated,
            toolbarTitle: CharSequence = uiState.toolbarTitle,
            @MenuRes altToolBarMenu: Int = uiState.altToolBarMenu,
            altToolBarShows: Boolean = uiState.altToolBarShows,
            altToolbarInvalidated: Boolean = uiState.altToolbarInvalidated,
            altToolbarTitle: CharSequence = uiState.altToolbarTitle,
            @ColorInt navBarColor: Int = uiState.navBarColor,
            systemUiShows: Boolean = uiState.systemUiShows,
            hasLightNavBar: Boolean = uiState.hasLightNavBar,
            fabClickListener: View.OnClickListener? = uiState.fabClickListener
    ) {
        uiState = uiState.copy(
                fabIcon = fabIcon,
                fabText = fabText,
                fabShows = fabShows,
                fabExtended = fabExtended,
                toolBarMenu = toolBarMenu,
                toolbarShows = toolbarShows,
                toolbarInvalidated = toolbarInvalidated,
                toolbarTitle = toolbarTitle,
                altToolBarMenu = altToolBarMenu,
                altToolBarShows = altToolBarShows,
                altToolbarInvalidated = altToolbarInvalidated,
                altToolbarTitle = altToolbarTitle,
                navBarColor = navBarColor,
                systemUiShows = systemUiShows,
                hasLightNavBar = hasLightNavBar,
                fabClickListener = fabClickListener
        )
    }

}
