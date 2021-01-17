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

package com.tunjid.rcswitchcontrol.control

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.tunjid.rcswitchcontrol.R


@SuppressLint("InflateParams")
class NameServiceDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = editTextDialog { editText, builder ->
        val nameListener = parentFragment as? ServiceNameListener

        builder
                .setTitle(R.string.name_nsd_service)
                .setPositiveButton(R.string.ok) { _, _ ->
                    nameListener?.onServiceNamed(editText.text.toString())
                    dismiss()
                }
    }


    interface ServiceNameListener {
        fun onServiceNamed(name: String)
    }

    companion object {

        fun newInstance(): NameServiceDialogFragment {

            val fragment = NameServiceDialogFragment()
            val args = Bundle()
            fragment.arguments = args
            return fragment
        }
    }
}
