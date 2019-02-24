package com.tunjid.rcswitchcontrol.utils;

import com.google.android.material.snackbar.Snackbar;
import com.tunjid.androidbootstrap.functions.Consumer;

import java.util.Stack;

/**
 * Handles queued deletion of a Switch
 */
public class DeletionHandler<T> extends Snackbar.Callback {

    private final int originalPosition;
    private final Consumer<DeletionHandler<T>> onDismissed;


    private Stack<T> deletedItems = new Stack<>();

   public DeletionHandler(int originalPosition, Consumer<DeletionHandler<T>> onDismissed) {
        this.originalPosition = originalPosition;
        this.onDismissed = onDismissed;
    }

    @Override
    public void onDismissed(Snackbar snackbar, int event) {
        onDismissed.accept(this);
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
