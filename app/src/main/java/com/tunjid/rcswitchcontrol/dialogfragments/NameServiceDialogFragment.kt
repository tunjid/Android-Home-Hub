package com.tunjid.rcswitchcontrol.dialogfragments

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.tunjid.rcswitchcontrol.R


@SuppressLint("InflateParams")
class NameServiceDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val activity = requireActivity()
        val nameListener = parentFragment as ServiceNameListener?
        val inflater = activity.layoutInflater

        val view = inflater.inflate(R.layout.dialog_rename_switch, null)
        val editText = view.findViewById<EditText>(R.id.switch_name)

        return AlertDialog.Builder(activity, R.style.DialogTheme)
                .setView(view)
                .setTitle(R.string.name_nsd_service)
                .setPositiveButton(R.string.ok) { _, _ ->
                    nameListener?.onServiceNamed(editText.text.toString())
                    dismiss()
                }
                .setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
                .create()
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
