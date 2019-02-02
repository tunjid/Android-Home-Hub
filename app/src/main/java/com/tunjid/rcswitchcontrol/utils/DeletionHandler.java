package com.tunjid.rcswitchcontrol.utils;

import com.google.android.material.snackbar.Snackbar;

import java.util.Stack;

/**
 * Handles queued deletion of a Switch
 */
public class DeletionHandler<T> extends Snackbar.Callback {

    private final int originalPosition;
    private final Runnable onDismissed;


    private Stack<T> deletedItems = new Stack<>();

   public DeletionHandler(int originalPosition, Runnable onDismissed) {
        this.originalPosition = originalPosition;
        this.onDismissed = onDismissed;
    }

    @Override
    public void onDismissed(Snackbar snackbar, int event) {
        onDismissed.run();
    }

    public boolean hasItems() {
       return !deletedItems.empty();
    }

    public int getDeletedPosition() {
       return originalPosition;
    }

   public void push(T item) {
        deletedItems.push(item);
    }

   public T pop() {
        return deletedItems.pop();
    }
}
