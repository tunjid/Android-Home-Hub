package com.tunjid.rcswitchcontrol.utils

import com.tunjid.rcswitchcontrol.App

object SpanCountCalculator {

    val spanCount: Int
        get() {
            val metrics = App.instance.resources.displayMetrics
            return if (metrics.widthPixels > metrics.heightPixels) 2 else 1
        }
}
