package com.tunjid.rcswitchcontrol.utils

import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import java.util.*

interface TransientBarController {
    val transientBarDriver: TransientBarDriver
}

class TransientBarDriver(
        private val coordinatorLayout: CoordinatorLayout
) : LifecycleEventObserver {

    private val transientBottomBars = ArrayList<BaseTransientBottomBar<*>>()

    private val callback = object : BaseTransientBottomBar.BaseCallback<BaseTransientBottomBar<*>>() {
        override fun onDismissed(bar: BaseTransientBottomBar<*>?, event: Int) {
            transientBottomBars.remove(bar)
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) = when (event) {
        Lifecycle.Event.ON_PAUSE -> clearTransientBars()
        Lifecycle.Event.ON_DESTROY -> source.lifecycle.removeObserver(this)
        else -> Unit
    }

    fun showSnackBar(consumer: (Snackbar) -> Unit) {
        val snackbar = Snackbar.make(coordinatorLayout, "", Snackbar.LENGTH_INDEFINITE).withCallback(callback)

        // Necessary to remove snackbar padding for keyboard on older versions of Android
        ViewCompat.setOnApplyWindowInsetsListener(snackbar.view) { _, insets -> insets }
        consumer.invoke(snackbar)
        snackbar.show()
    }

    private fun clearTransientBars() {
        for (bar in transientBottomBars) bar.dismiss()
        transientBottomBars.clear()
    }

}

@Suppress("UNCHECKED_CAST")
fun <T : BaseTransientBottomBar<T>> BaseTransientBottomBar<T>.withCallback(callback: BaseTransientBottomBar.BaseCallback<BaseTransientBottomBar<*>>): T =
        addCallback(callback as BaseTransientBottomBar.BaseCallback<T>)