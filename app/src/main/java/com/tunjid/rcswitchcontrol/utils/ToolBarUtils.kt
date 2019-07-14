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

package com.tunjid.rcswitchcontrol.utils

import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import com.tunjid.rcswitchcontrol.R

fun Toolbar.update(menu: Int, title: CharSequence) {
    if (id == R.id.alt_toolbar || childCount <= 2) {
        setTitle(title)
        replaceMenu(menu)
    } else for (i in 0 until childCount) {
        val child = getChildAt(i)
        if (child is ImageView) continue

        child.animate().alpha(0f).setDuration(TOOLBAR_ANIM_DELAY.toLong()).withEndAction {
            if (child is TextView) setTitle(title)
            else if (child is ActionMenuView) replaceMenu(menu)

            child.animate()
                    .setDuration(TOOLBAR_ANIM_DELAY.toLong())
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .alpha(1f)
                    .start()
        }.start()
    }
}

fun Toolbar.replaceMenu(menu: Int) {
    this.menu.clear()
    if (menu != 0) inflateMenu(menu)
}

const val TOOLBAR_ANIM_DELAY = 200
