package com.tunjid.rcswitchcontrol.utils

import com.google.android.material.snackbar.Snackbar
import java.util.*

/**
 * Handles queued deletion of a Switch
 */
class DeletionHandler<T>(
        val deletedPosition: Int,
        private val onDismissed: (self: DeletionHandler<T>) -> Unit) : Snackbar.Callback() {

    private val deletedItems = Stack<T>()

    override fun onDismissed(snackbar: Snackbar?, event: Int) = onDismissed.invoke(this)

    fun hasItems(): Boolean = deletedItems.isNotEmpty()

    fun pop(): T = deletedItems.pop()

    fun peek(): T = deletedItems.peek()

    fun push(item: T) = deletedItems.push(item).let { Unit }
}
