package com.tunjid.rcswitchcontrol.dialogfragments;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.tunjid.rcswitchcontrol.R;


@SuppressLint("InflateParams")
public class NameServiceDialogFragment extends DialogFragment {

    public static NameServiceDialogFragment newInstance() {

        NameServiceDialogFragment fragment = new NameServiceDialogFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    public NameServiceDialogFragment() {

    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        final ServiceNameListener nameListener = (ServiceNameListener) getParentFragment();
        final LayoutInflater inflater = getActivity().getLayoutInflater();

        final View view = inflater.inflate(R.layout.dialog_rename_switch, null);
        final EditText editText = (EditText) view.findViewById(R.id.switch_name);

        return new AlertDialog.Builder(getActivity()).setTitle(R.string.name_nsd_service)
                .setView(view)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        nameListener.onServiceNamed(editText.getText().toString());
                        dismiss();
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dismiss();
                    }
                })
                .create();
    }

    public interface ServiceNameListener {
        void onServiceNamed(String name);
    }
}
