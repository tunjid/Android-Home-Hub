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

package com.tunjid.rcswitchcontrol.ui

import android.graphics.Color
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.dynamicanimation.animation.SpringAnimation
import com.tunjid.globalui.InsetDescriptor
import com.tunjid.globalui.InsetFlags
import com.tunjid.globalui.NoOpSystemUI
import com.tunjid.globalui.SystemUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlin.reflect.KMutableProperty0

fun KMutableProperty0<UiState>.updatePartial(updater: UiState.() -> UiState) =
    set(updater.invoke(get()))

interface RootUiController {
    var uiState: UiState
    val liveUiState: StateFlow<UiState>
}

data class ToolbarIcon(
    val id: Int,
    val text: String,
    val imageVector: ImageVector? = null,
    val contentDescription: String? = null,
)

data class UiState(
    val toolbarIcons: List<ToolbarIcon> = listOf(),
    val toolbarShows: Boolean = false,
    val toolbarOverlaps: Boolean = false,
    val toolbarInvalidated: Boolean = false,
    val toolbarTitle: CharSequence = "",
    val altToolbarIcons: List<ToolbarIcon> = listOf(),
    val altToolbarShows: Boolean = false,
    val altToolbarOverlaps: Boolean = false,
    val altToolbarInvalidated: Boolean = false,
    val altToolbarTitle: CharSequence = "",
    @param:DrawableRes
    @field:DrawableRes
    @get:DrawableRes
    val fabIcon: Int = 0,
    val fabShows: Boolean = false,
    val fabExtended: Boolean = true,
    val fabText: CharSequence = "",
    @param:ColorInt
    @field:ColorInt
    @get:ColorInt
    val backgroundColor: Int = Color.TRANSPARENT,
    val snackbarText: CharSequence = "",
    @param:ColorInt
    @field:ColorInt
    @get:ColorInt
    val navBarColor: Int = Color.BLACK,
    val lightStatusBar: Boolean = false,
    val showsBottomNav: Boolean? = null,
    val insetFlags: InsetDescriptor = InsetFlags.ALL,
    val systemUI: SystemUI = NoOpSystemUI,
    val fabClickListener: (View) -> Unit = emptyCallback(),
    val fabTransitionOptions: SpringAnimation.() -> Unit = emptyCallback(),
    val toolbarMenuClickListener: (ToolbarIcon) -> Unit = emptyCallback(),
    val altToolbarMenuClickListener: (ToolbarIcon) -> Unit = emptyCallback(),
)

private fun <T> emptyCallback(): (T) -> Unit = {}

// Internal state slices for memoizing animations.
// They aggregate the parts of Global UI they react to

internal data class ToolbarState(
    val icons: List<ToolbarIcon>,
    val visible: Boolean,
    val overlaps: Boolean,
    val toolbarTitle: CharSequence,
    val toolbarInvalidated: Boolean
)

internal data class SnackbarPositionalState(
    val bottomNavVisible: Boolean,
    override val bottomInset: Int,
    override val navBarSize: Int,
    override val insetDescriptor: InsetDescriptor
) : KeyboardAware

internal data class FabPositionalState(
    val fabVisible: Boolean,
    val bottomNavVisible: Boolean,
    val snackbarHeight: Int,
    override val bottomInset: Int,
    override val navBarSize: Int,
    override val insetDescriptor: InsetDescriptor
) : KeyboardAware

internal data class FragmentContainerPositionalState(
    val statusBarSize: Int,
    val toolbarOverlaps: Boolean,
    val bottomNavVisible: Boolean,
    override val bottomInset: Int,
    override val navBarSize: Int,
    override val insetDescriptor: InsetDescriptor
) : KeyboardAware

internal data class BottomNavPositionalState(
    val insetDescriptor: InsetDescriptor,
    val bottomNavVisible: Boolean,
    val navBarSize: Int
)

internal val UiState.toolbarState
    get() = ToolbarState(
        icons = toolbarIcons,
        toolbarTitle = toolbarTitle,
        visible = toolbarShows,
        overlaps = toolbarOverlaps,
        toolbarInvalidated = toolbarInvalidated
    )

internal val UiState.altToolbarState
    get() = ToolbarState(
        icons = altToolbarIcons,
        toolbarTitle = altToolbarTitle,
        visible = altToolbarShows,
        overlaps = altToolbarOverlaps,
        toolbarInvalidated = altToolbarInvalidated
    )

internal val UiState.fabState
    get() = FabPositionalState(
        fabVisible = fabShows,
        snackbarHeight = systemUI.dynamic.snackbarHeight,
        bottomNavVisible = showsBottomNav == true,
        bottomInset = systemUI.dynamic.bottomInset,
        navBarSize = systemUI.static.navBarSize,
        insetDescriptor = insetFlags
    )

internal val UiState.snackbarPositionalState
    get() = SnackbarPositionalState(
        bottomNavVisible = showsBottomNav == true,
        bottomInset = systemUI.dynamic.bottomInset,
        navBarSize = systemUI.static.navBarSize,
        insetDescriptor = insetFlags
    )

internal val UiState.fabGlyphs
    get() = fabIcon to fabText

internal val UiState.toolbarPosition
    get() = systemUI.static.statusBarSize

internal val UiState.bottomNavPositionalState
    get() = BottomNavPositionalState(
        bottomNavVisible = showsBottomNav == true,
        navBarSize = systemUI.static.navBarSize,
        insetDescriptor = insetFlags
    )

internal val UiState.fragmentContainerState
    get() = FragmentContainerPositionalState(
        statusBarSize = systemUI.dynamic.topInset,
        insetDescriptor = insetFlags,
        toolbarOverlaps = toolbarOverlaps,
        bottomNavVisible = showsBottomNav == true,
        bottomInset = systemUI.dynamic.bottomInset,
        navBarSize = systemUI.static.navBarSize
    )

/**
 * Interface for [UiState] state slices that are aware of the keyboard. Useful for
 * keyboard visibility changes for bottom aligned views like Floating Action Buttons and Snack Bars
 */
interface KeyboardAware {
    val bottomInset: Int
    val navBarSize: Int
    val insetDescriptor: InsetDescriptor
}

internal val KeyboardAware.keyboardSize get() = bottomInset - navBarSize

val CoroutineScope.a: Int
    get() {
        this.isActive
        this.coroutineContext.isActive
        return 0
    }

@Composable
fun <T> aware(action: (T) -> Unit) {
    val mutableState: MutableState<(T) -> Unit> = remember { mutableStateOf(action) }

    DisposableEffect(true) {
        onDispose {
            mutableState.value = {}
        }
    }
}