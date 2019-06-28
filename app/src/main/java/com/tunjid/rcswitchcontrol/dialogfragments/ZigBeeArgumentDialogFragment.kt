package com.tunjid.rcswitchcontrol.dialogfragments

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.data.ZigBeeCommandArgs
import com.tunjid.rcswitchcontrol.data.ZigBeeCommandInfo


@SuppressLint("InflateParams")
class ZigBeeArgumentDialogFragment : DialogFragment() {

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
        val recyclerView = view.findViewById<RecyclerView>(R.id.list)
        recyclerView.adapter = Adapter(commandInfo)
        recyclerView.layoutManager = LinearLayoutManager(view.context)

        return AlertDialog.Builder(activity, R.style.DialogTheme)
                .setView(view)
                .setTitle(getString(R.string.zigbee_command_arguments_title, commandInfo.command))
                .setPositiveButton(R.string.ok) { _, _ -> listener?.onArgsEntered(commandInfo.toArgs()) }
                .setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
                .create()
    }

    interface ZigBeeArgsListener {
        fun onArgsEntered(args: ZigBeeCommandArgs)
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

    class Adapter(private val commandInfo: ZigBeeCommandInfo) : RecyclerView.Adapter<ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
                ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.dialog_rename_switch, parent, false))


        override fun getItemCount(): Int =
                commandInfo.entries.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) =
                holder.bind(commandInfo.entries[position])

        override fun onViewRecycled(holder: ViewHolder) = holder.unBind()

        override fun onFailedToRecycleView(holder: ViewHolder): Boolean {
            holder.unBind()
            return super.onFailedToRecycleView(holder)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), TextWatcher {

        private lateinit var entry: ZigBeeCommandInfo.Entry
        private val editText = itemView as EditText

        fun bind(entry: ZigBeeCommandInfo.Entry) {
            this.entry = entry

            editText.hint = entry.key
            editText.setText(entry.value)
            editText.addTextChangedListener(this)
        }

        fun unBind() = editText.removeTextChangedListener(this)

        override fun afterTextChanged(s: Editable) {
            entry.value = s.toString()
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
    }
}
