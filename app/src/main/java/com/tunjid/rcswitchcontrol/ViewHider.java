package com.tunjid.rcswitchcontrol;


import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Interpolator;

import static android.view.View.VISIBLE;

/**
 * Created by tj.dahunsi on 2/19/16.
 * <p>
 * An object for hiding views.
 */
public class ViewHider {

    private static final Interpolator FAST_OUT_SLOW_IN_INTERPOLATOR = new FastOutSlowInInterpolator();

    private boolean mVisible;
    private View view;

    public ViewHider(View view) {
        this.view = view;
    }

    public void showTranslate() {
        this.toggle(true);
    }

    public void hideTranslate() {
        this.toggle(false);
    }

    private void toggle(final boolean visible) {
        if (this.mVisible != visible) {

            this.mVisible = visible;
            int height = view.getHeight();

            if (height == 0) {
                ViewTreeObserver translationY = view.getViewTreeObserver();
                if (translationY.isAlive()) {
                    translationY.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                        public boolean onPreDraw() {
                            ViewTreeObserver currentVto = view.getViewTreeObserver();
                            if (currentVto.isAlive()) {
                                currentVto.removeOnPreDrawListener(this);
                            }
                            ViewHider.this.toggle(visible);
                            return true;
                        }
                    });
                    return;
                }
            }

            int shownHeight = (height + getMarginBottom()); // the margin is negative
            int translationY1 = visible ? shownHeight : 0;

            if (view.getVisibility() == VISIBLE && !visible) {
                view.setTranslationY(shownHeight);
                view.setVisibility(VISIBLE);
                view.animate()
                        .translationY(0F)
                        .setDuration(200L)
                        .setInterpolator(FAST_OUT_SLOW_IN_INTERPOLATOR);
            }
            else {
                view.animate()
                        .translationY((float) translationY1)
                        .setDuration(200L)
                        .setInterpolator(FAST_OUT_SLOW_IN_INTERPOLATOR);
            }
        }
    }

    private int getMarginBottom() {
        int marginBottom = 0;
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            marginBottom = ((ViewGroup.MarginLayoutParams) layoutParams).bottomMargin;
        }

        return marginBottom;
    }
}