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

package com.tunjid.rcswitchcontrol.dialogfragments

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Handler
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.os.postDelayed
import androidx.fragment.app.DialogFragment
import com.flask.colorpicker.builder.ColorPickerDialogBuilder
import com.tunjid.rcswitchcontrol.R

@SuppressLint("InflateParams")

fun DialogFragment.editTextDialog(receiver: (EditText, AlertDialog.Builder) -> AlertDialog.Builder): Dialog {
    val activity = requireActivity()
    val inflater = activity.layoutInflater

    val view = inflater.inflate(R.layout.dialog_rename_switch, null)
    val editText = view.findViewById<EditText>(R.id.switch_name)

    return receiver.invoke(
            editText,
            AlertDialog.Builder(activity, R.style.DialogTheme)
                    .setView(view)
                    .setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
    ).create()
}


fun ColorPickerDialogBuilder.throttleColorChanges(listener: (Int) -> Unit): ColorPickerDialogBuilder {
    val throttle = Throttle(listener)
    setOnColorChangedListener { throttle.run(it) }

    return this
}

internal class Throttle(private val listener: (Int) -> Unit) {

    private var cache = 0
    private var fired = false
    private val handler = Handler()

    internal fun run(it: Int) {
        if (fired) cache = it else {
            listener.invoke(it)
            fired = true
            cache = 0
            handler.postDelayed(200) {
                fired = false
                if (cache != 0) run(cache)
            }
        }
    }
}