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

package com.tunjid.rcswitchcontrol.viewholders

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import com.rcswitchcontrol.protocols.models.Device
import com.tunjid.androidx.recyclerview.viewbinding.BindingViewHolder

interface DeviceLongClickListener {
    fun onClicked(device: Device)

    fun onLongClicked(device: Device): Boolean

    fun onSwitchToggled(device: Device, state: Boolean)

    fun isSelected(device: Device): Boolean
}

interface DeviceAdapterListener :
        DeviceLongClickListener,
        RfDeviceListener,
        ZigBeeDeviceListener

fun Device.highlightViewHolder(holder: BindingViewHolder<*>, selectionFunction: (Device) -> Boolean) {
    val isSelected = selectionFunction.invoke(this)
    holder.scale(isSelected)
}

fun Device.performLongClick(holder: BindingViewHolder<*>, listener: DeviceLongClickListener): Boolean {
    highlightViewHolder(holder, listener::onLongClicked)
    return true
}

private fun BindingViewHolder<*>.scale(isSelected: Boolean) {
    val end = if (isSelected) FOUR_FIFTH_SCALE else FULL_SCALE

    val set = AnimatorSet()
    val scaleDownX = animateProperty(SCALE_X_PROPERTY, itemView.scaleX, end)
    val scaleDownY = animateProperty(SCALE_Y_PROPERTY, itemView.scaleY, end)

    set.playTogether(scaleDownX, scaleDownY)
    set.start()
}

private fun BindingViewHolder<*>.animateProperty(property: String, start: Float, end: Float): ObjectAnimator {
    return ObjectAnimator.ofFloat(itemView, property, start, end).setDuration(DURATION.toLong())
}

private const val FULL_SCALE = 1f
private const val FOUR_FIFTH_SCALE = 0.8f
private const val DURATION = 200
private const val SCALE_X_PROPERTY = "scaleX"
private const val SCALE_Y_PROPERTY = "scaleY"
