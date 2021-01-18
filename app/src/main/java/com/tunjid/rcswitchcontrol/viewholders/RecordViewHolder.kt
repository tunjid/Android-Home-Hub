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

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.ViewGroup
import com.tunjid.androidx.recyclerview.viewbinding.BindingViewHolder
import com.tunjid.androidx.recyclerview.viewbinding.viewHolderDelegate
import com.tunjid.androidx.recyclerview.viewbinding.viewHolderFrom
import com.tunjid.rcswitchcontrol.control.Record
import com.tunjid.rcswitchcontrol.databinding.ViewholderCommandBinding
import com.tunjid.rcswitchcontrol.databinding.ViewholderHistoryBinding
import com.tunjid.rcswitchcontrol.utils.makeAccessibleForTV


fun ViewGroup.historyViewHolder() = viewHolderFrom(ViewholderHistoryBinding::inflate).apply {
    binding.text.apply {
        makeAccessibleForTV(stroked = true)
        isClickable = false
        strokeColor = ColorStateList.valueOf(Color.WHITE)
    }
}

fun ViewGroup.commandViewHolder(listener: ((Record) -> Unit)) = viewHolderFrom(ViewholderCommandBinding::inflate).apply {
    binding.text.apply {
        makeAccessibleForTV(stroked = true)
        setOnClickListener { listener(record) }
        strokeColor = ColorStateList.valueOf(Color.WHITE)
    }
}

fun BindingViewHolder<ViewholderHistoryBinding>.bind(record: Record) {
    binding.text.text = record.entry
}

private var BindingViewHolder<ViewholderCommandBinding>.record by viewHolderDelegate<Record>()

fun BindingViewHolder<ViewholderCommandBinding>.bindCommand(record: Record) = binding.run {
    this@bindCommand.record = record
    text.text = record.entry
}
