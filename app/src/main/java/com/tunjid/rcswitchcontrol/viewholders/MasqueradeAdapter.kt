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

import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.core.view.forEach
import androidx.recyclerview.widget.RecyclerView
import com.tunjid.androidx.recyclerview.AbstractListManagerBuilder
import com.tunjid.androidx.recyclerview.ListManager
import com.tunjid.rcswitchcontrol.utils.WindowInsetsDriver.Companion.bottomInset

/**
 * A Proxy Adapter that adds extra items to the bottom of the actual adapter for over scrolling
 * to easily compensate for going edge to edge
 */
class MasqueradeAdapter<T : RecyclerView.ViewHolder>(
        private val proxyAdapter: RecyclerView.Adapter<T>,
        private val extras: Int)
    : RecyclerView.Adapter<T>() {

    init {
        setHasStableIds(proxyAdapter.hasStableIds())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): T =
            proxyAdapter.onCreateViewHolder(parent, viewType)

    override fun getItemCount(): Int = proxyAdapter.itemCount + extras

    override fun getItemId(position: Int): Long =
            if (position < proxyAdapter.itemCount) proxyAdapter.getItemId(position)
            else Long.MAX_VALUE - (position - proxyAdapter.itemCount)

    override fun getItemViewType(position: Int): Int =
            if (position < proxyAdapter.itemCount) proxyAdapter.getItemViewType(position)
            else super.getItemViewType(position)

    override fun onBindViewHolder(holder: T, position: Int) {
        val isFromProxy = position < proxyAdapter.itemCount
        holder.itemView.adjustSpacers(isFromProxy)

        if (isFromProxy) proxyAdapter.onBindViewHolder(holder, position)
    }

    override fun onBindViewHolder(holder: T, position: Int, payloads: MutableList<Any>) {
        val isFromProxy = position < proxyAdapter.itemCount
        holder.itemView.adjustSpacers(isFromProxy)

        if (isFromProxy) proxyAdapter.onBindViewHolder(holder, position, payloads)
    }

    override fun unregisterAdapterDataObserver(observer: RecyclerView.AdapterDataObserver) = super.unregisterAdapterDataObserver(observer)
            .apply { proxyAdapter.unregisterAdapterDataObserver(observer) }

    override fun onViewDetachedFromWindow(holder: T) = proxyAdapter.onViewDetachedFromWindow(holder)

    override fun setHasStableIds(hasStableIds: Boolean) = super.setHasStableIds(hasStableIds)
            .apply { proxyAdapter.setHasStableIds(hasStableIds) }

    override fun onFailedToRecycleView(holder: T): Boolean =
            proxyAdapter.onFailedToRecycleView(holder)

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) =
            proxyAdapter.onAttachedToRecyclerView(recyclerView)

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) =
            proxyAdapter.onDetachedFromRecyclerView(recyclerView)

    override fun onViewRecycled(holder: T) = proxyAdapter.onViewRecycled(holder)

    override fun registerAdapterDataObserver(observer: RecyclerView.AdapterDataObserver) = super.registerAdapterDataObserver(observer)
            .apply { proxyAdapter.registerAdapterDataObserver(observer) }

    override fun onViewAttachedToWindow(holder: T) = proxyAdapter.onViewAttachedToWindow(holder)

    private fun View.adjustSpacers(isFromProxy: Boolean) {
        visibility = if (isFromProxy) VISIBLE else INVISIBLE
        layoutParams.height = if (isFromProxy) RecyclerView.LayoutParams.WRAP_CONTENT else bottomInset
        (this as? ViewGroup)?.forEach { it.visibility = if (isFromProxy) VISIBLE else GONE }
    }

}

fun <
        B : AbstractListManagerBuilder<B, S, VH, T>,
        S : ListManager<VH, T>,
        VH : RecyclerView.ViewHolder, T>
        AbstractListManagerBuilder<B, S, VH, T>.withPaddedAdapter(
        adapter: RecyclerView.Adapter<VH>,
        extras: Int = 1): B = withAdapter(MasqueradeAdapter(adapter, extras))