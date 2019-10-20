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

package com.tunjid.rcswitchcontrol.adapters

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.tunjid.androidx.recyclerview.InteractiveViewHolder
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.data.Device
import com.tunjid.rcswitchcontrol.utils.unwrapped

// ViewHolder for actual content
open class DeviceViewHolder<T : DeviceLongClickListener, S : Device> internal constructor(
        itemView: View,
        listener: T
) : InteractiveViewHolder<T>(itemView, listener) {

    open lateinit var device: S

    open var deviceName: TextView = itemView.findViewById(R.id.switch_name)
    open var offSwitch: View = itemView.findViewById(R.id.off_switch)
    open var onSwitch: View = itemView.findViewById(R.id.on_switch)
    private var cardView = (itemView as MaterialCardView)

    init {
        cardView.setOnClickListener { delegate?.onClicked(device) }
        cardView.setOnLongClickListener {
            performLongClick()
            true
        }
    }

    open fun bind(device: S) {
        this.device = device
        highlightViewHolder(delegate.unwrapped::isSelected)
    }

    fun performLongClick(): Boolean {
        highlightViewHolder(delegate.unwrapped::onLongClicked)
        return true
    }

    private fun highlightViewHolder(selectionFunction: (Device) -> Boolean) {
        val isSelected = selectionFunction.invoke(device)
        scale(isSelected)
    }

    private fun scale(isSelected: Boolean) {
        val end = if (isSelected) FOUR_FIFTH_SCALE else FULL_SCALE

        val set = AnimatorSet()
        val scaleDownX = animateProperty(SCALE_X_PROPERTY, itemView.scaleX, end)
        val scaleDownY = animateProperty(SCALE_Y_PROPERTY, itemView.scaleY, end)

        set.playTogether(scaleDownX, scaleDownY)
        set.start()
    }

    private fun animateProperty(property: String, start: Float, end: Float): ObjectAnimator {
        return ObjectAnimator.ofFloat(itemView, property, start, end).setDuration(DURATION.toLong())
    }

    companion object {
        private const val FULL_SCALE = 1f
        private const val FOUR_FIFTH_SCALE = 0.8f
        private const val DURATION = 200
        private const val SCALE_X_PROPERTY = "scaleX"
        private const val SCALE_Y_PROPERTY = "scaleY"
    }
}