package com.tunjid.rcswitchcontrol.utils

import android.view.View
import androidx.dynamicanimation.animation.SpringAnimation
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.tunjid.androidx.view.util.spring
import com.tunjid.androidx.view.util.viewDelegate
import com.tunjid.rcswitchcontrol.App
import com.tunjid.rcswitchcontrol.R

fun View.makeAccessibleForTV(stroked: Boolean = false, onFocused: ((Any?) -> Unit)? = null) {
    if (!App.isAndroidTV) return

    isFocusable = true
    isFocusableInTouchMode = true
    setOnFocusChangeListener { _, hasFocus ->
        spring(SpringAnimation.SCALE_Y).animateToFinalPosition(if (hasFocus) 1.1F else 1F)
        spring(SpringAnimation.SCALE_X).animateToFinalPosition(if (hasFocus) 1.1F else 1F)

        if (stroked) stroke =
            if (hasFocus) context.resources.getDimensionPixelSize(R.dimen.quarter_margin)
            else 0

        val frozen = item
        if (hasFocus && frozen != null && onFocused != null) onFocused(frozen)
    }
}

var View.item by viewDelegate<Any?>()

private var View.stroke: Int
    get() = when (this) {
        is MaterialButton -> strokeWidth
        is MaterialCardView -> strokeWidth
        else -> 0
    }
    set(value) = when (this) {
        is MaterialButton -> strokeWidth = value
        is MaterialCardView -> strokeWidth = value
        else -> Unit
    }