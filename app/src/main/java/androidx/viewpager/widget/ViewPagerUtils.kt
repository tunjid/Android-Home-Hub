package androidx.viewpager.widget

import android.view.View

val ViewPager.currentView: View?
    get() {
        val currentItem = currentItem
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val layoutParams = child.layoutParams as ViewPager.LayoutParams
            if (!layoutParams.isDecor && currentItem == layoutParams.position) {
                return child
            }
        }
        return null
    }