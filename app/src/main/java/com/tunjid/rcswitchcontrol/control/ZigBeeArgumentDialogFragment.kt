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
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rcswitchcontrol.zigbee.models.ZigBeeCommand
import com.rcswitchcontrol.zigbee.models.ZigBeeCommandInfo
import com.tunjid.androidx.recyclerview.adapterOf
import com.tunjid.androidx.view.util.inflate
import com.tunjid.rcswitchcontrol.R


@SuppressLint("InflateParams")
class ZigBeeArgumentDialogFragment : DialogFragment() {

    private var done = 0L
    private lateinit var commandInfo: ZigBeeCommandInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        commandInfo = arguments!!.getParcelable(COMMAND_INFO)!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (view as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        val listener = parentFragment as ZigBeeArgsListener?
        val inflater = activity.layoutInflater

        val view = inflater.inflate(R.layout.dialog_zigbee_args, null)
        view.findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(view.context)
            adapter = adapterOf(
                    itemsSource = commandInfo::entries,
                    viewHolderCreator = { parent, _ -> ViewHolder(parent.inflate(R.layout.dialog_rename_switch), this@ZigBeeArgumentDialogFragment::push) },
                    viewHolderBinder = { holder, entry, _ -> holder.bind(entry) },
                    onViewHolderRecycled = ViewHolder::unbind,
                    onViewHolderRecycleFailed = { it.unbind().let { false } }
            )
        }

        return AlertDialog.Builder(activity, R.style.DialogTheme)
                .setView(view)
                .setTitle(getString(R.string.zigbee_command_arguments_title, commandInfo.command))
                .setPositiveButton(R.string.ok) { _, _ -> listener?.onArgsEntered(commandInfo.toArgs()) }
                .setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
                .create()
    }

    override fun onStart() {
        super.onStart()
        push()
        check()
    }

    private fun check() {
        System.currentTimeMillis().apply {
            if (this >= done) dismiss()
            else dialog?.window?.decorView?.postDelayed(this@ZigBeeArgumentDialogFragment::check, this - done)
        }
    }

    private fun push() {
        done = System.currentTimeMillis() + 20000
    }

    interface ZigBeeArgsListener {
        fun onArgsEntered(command: ZigBeeCommand)
    }

    companion object {

        private const val COMMAND_INFO = "COMMAND_INFO"

        fun newInstance(commandInfo: ZigBeeCommandInfo): ZigBeeArgumentDialogFragment {

            val fragment = ZigBeeArgumentDialogFragment()
            val args = Bundle()
            args.putParcelable(COMMAND_INFO, commandInfo)
            fragment.arguments = args
            return fragment
        }
    }

    class ViewHolder(
            itemView: View,
            private val listener: () -> Unit
    ) : RecyclerView.ViewHolder(itemView), TextWatcher {

        private lateinit var entry: ZigBeeCommandInfo.Entry
        private val editText = itemView as EditText

        fun bind(entry: ZigBeeCommandInfo.Entry) {
            this.entry = entry

            editText.hint = entry.key
            editText.setText(entry.value)
            editText.addTextChangedListener(this)
        }

        fun unbind() = editText.removeTextChangedListener(this)

        override fun afterTextChanged(s: Editable) {
            entry.value = s.toString()
            listener()
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
    }
}
