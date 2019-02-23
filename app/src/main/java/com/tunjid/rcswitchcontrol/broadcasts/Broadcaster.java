package com.tunjid.rcswitchcontrol.broadcasts;

import android.content.Intent;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.processors.PublishProcessor;

public class Broadcaster {

    private static String TAG = "Broadcaster";
    private static Broadcaster instance;

    private final PublishProcessor<Intent> processor;

    private Broadcaster() {
        processor = PublishProcessor.create();
    }

    private static Broadcaster getInstance() {
        if (instance == null) instance = new Broadcaster();
        return instance;
    }

    public static Flowable<Intent> listen(String... filters) {
        return listen(Arrays.asList(filters));
    }

    public static Flowable<Intent> listen(List<String> filters) {
        return getInstance().processor
                .doOnNext(intent -> Log.i(TAG, "Received data for: " + intent.getAction()))
                .filter(intent -> filters.contains(intent.getAction()));
    }

    public static void push(Intent intent) {
        getInstance().processor.onNext(intent);
    }
}
