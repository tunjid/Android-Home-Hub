package com.tunjid.rcswitchcontrol.dialogfragments

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.tunjid.rcswitchcontrol.R
import com.tunjid.rcswitchcontrol.data.RfSwitch


@SuppressLint("InflateParams")
class RenameSwitchDialogFragment : DialogFragment() {

    private lateinit var rfSwitch: RfSwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rfSwitch = arguments!!.getParcelable(SWITCH)!!
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        val listener = parentFragment as SwitchNameListener?
        val inflater = activity.layoutInflater

        val view = inflater.inflate(R.layout.dialog_rename_switch, null)
        val editText = view.findViewById<EditText>(R.id.switch_name)

        editText.setText(rfSwitch.name)

        return AlertDialog.Builder(activity, R.style.DialogTheme)
                .setView(view)
                .setTitle(R.string.rename_switch)
                .setPositiveButton(R.string.rename) { _, _ ->
                    rfSwitch.name = editText.text.toString()
                    listener?.onSwitchRenamed(rfSwitch)
                    dismiss()
                }
                .setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
                .create()
    }

    interface SwitchNameListener {
        fun onSwitchRenamed(rfSwitch: RfSwitch)
    }

    companion object {

        private const val SWITCH = "SWITCH"

        fun newInstance(rfSwitch: RfSwitch): RenameSwitchDialogFragment {

            val fragment = RenameSwitchDialogFragment()
            val args = Bundle()
            args.putParcelable(SWITCH, rfSwitch)
            fragment.arguments = args
            return fragment
        }
    }
}
