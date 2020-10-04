package com.google.android.material.bottomsheet

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.get
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.lang.ref.WeakReference

class ViewPagerBottomSheetBehavior<V : View> : BottomSheetBehavior<V> {

    @Suppress("unused")
    constructor() : super()

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    override fun findScrollingChild(view: View): View? {
        if (ViewCompat.isNestedScrollingEnabled(view)) return view

        if (view is ViewPager2) {
            val recyclerView = view[0] as? RecyclerView ?: return null
            val currentViewPagerChild = recyclerView.findViewHolderForLayoutPosition(view.currentItem)?.itemView
                    ?: return null

            val scrollingChild = findScrollingChild(currentViewPagerChild)
            if (scrollingChild != null) return scrollingChild
        } else if (view is ViewGroup) {
            var i = 0
            val count = view.childCount
            while (i < count) {
                val scrollingChild = findScrollingChild(view.getChildAt(i))
                if (scrollingChild != null) return scrollingChild
                i++
            }
        }
        return null
    }

    fun invalidateScrollingChild() {
        val view = viewRef?.get() ?: return
        val scrollingChild = findScrollingChild(view)
        nestedScrollingChildRef = WeakReference(scrollingChild)
    }
}

fun ViewPager2.setupForBottomSheet() {
    val bottomSheetParent = findBottomSheetParent()
    if (bottomSheetParent != null) {
        registerOnPageChangeCallback(BottomSheetViewPagerListener(this, bottomSheetParent))
    }
}

private class BottomSheetViewPagerListener(private val viewPager: ViewPager2, bottomSheetParent: View) : ViewPager2.OnPageChangeCallback() {

    private val behavior = (bottomSheetParent.layoutParams as CoordinatorLayout.LayoutParams).behavior as ViewPagerBottomSheetBehavior


    override fun onPageSelected(position: Int) {
        viewPager.post(behavior::invalidateScrollingChild)
    }
}

private fun View.findBottomSheetParent(): View? {
    var current: View? = this

    while (current != null) {
        val params = current.layoutParams
        if (params is CoordinatorLayout.LayoutParams && params.behavior is ViewPagerBottomSheetBehavior) {
            return current
        }

        val parent = current.parent
        current = if (parent is View) parent
        else null
    }

    return null
}
