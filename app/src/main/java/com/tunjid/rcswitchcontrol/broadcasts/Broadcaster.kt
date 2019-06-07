package com.tunjid.rcswitchcontrol.broadcasts

import android.content.Intent
import android.util.Log
import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import java.util.*

class Broadcaster private constructor() {

    private val processor: PublishProcessor<Intent> = PublishProcessor.create()

    companion object {

        private val TAG = "Broadcaster"
        val instance: Broadcaster by lazy { Broadcaster() }


        fun listen(vararg filters: String): Flowable<Intent> = listen(Arrays.asList(*filters))

        fun listen(filters: List<String>): Flowable<Intent> {
            return instance.processor
                    .doOnNext { intent -> Log.i(TAG, "Received data for: " + intent.action) }
                    .filter { intent -> filters.contains(intent.action) }
        }

        fun push(intent: Intent) = instance.processor.onNext(intent)
    }
}
