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
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.rcswitchcontrol.protocols.Name
import com.rcswitchcontrol.protocols.renamePayload
import com.tunjid.androidx.core.delegates.fragmentArgs
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.viewmodels.ControlViewModel

@SuppressLint("InflateParams")
class RenameSwitchDialogFragment : DialogFragment() {

    private var name by fragmentArgs<Name>()
    private val viewModel by activityViewModels<ControlViewModel>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = editTextDialog { editText, builder ->
        editText.setText(name.value)

        builder
            .setTitle(R.string.rename_switch)
            .setPositiveButton(R.string.rename) { _, _ ->
                viewModel.dispatchPayload(name.copy(value = editText.text.toString()).renamePayload)
                dismiss()
            }
    }

    companion object {
        fun newInstance(name: Name): RenameSwitchDialogFragment = RenameSwitchDialogFragment().apply {
            this.name = name
        }
    }
}
