package com.tunjid.rcswitchcontrol;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;
import com.tunjid.androidbootstrap.material.behavior.BottomTransientBarBehavior;

/**
 * Animates a {@link View} when a {@link Snackbar} appears.
 * <p>
 * Mostly identical to {@link com.google.android.material.floatingactionbutton.FloatingActionButton}
 * <p>
 * Created by tj.dahunsi on 4/15/17.
 */
@SuppressWarnings("unused") // Constructed via xml
public class SnackBarBehavior extends BottomTransientBarBehavior {
    public SnackBarBehavior(Context context, AttributeSet attrs) { super(context, attrs); }
}
