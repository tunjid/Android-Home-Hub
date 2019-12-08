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

package com.tunjid.rcswitchcontrol.common

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
