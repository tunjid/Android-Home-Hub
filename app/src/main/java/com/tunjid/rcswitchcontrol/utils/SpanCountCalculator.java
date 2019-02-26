package com.tunjid.rcswitchcontrol.utils;

import android.util.DisplayMetrics;

import com.tunjid.rcswitchcontrol.App;

public class SpanCountCalculator {

    public static int getSpanCount() {
        DisplayMetrics metrics = App.getInstance().getResources().getDisplayMetrics();
        return metrics.widthPixels > metrics.heightPixels ? 2 : 1;
    }
}
