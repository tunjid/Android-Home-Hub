package com.tunjid.rcswitchcontrol.fragments;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.model.RfSwitch;


@SuppressLint("InflateParams")
public class RenameSwitchDialogFragment extends DialogFragment {

    private static final String SWITCH = "SWITCH";

    private RfSwitch rfSwitch;

    public static RenameSwitchDialogFragment newInstance(RfSwitch rfSwitch) {

        RenameSwitchDialogFragment fragment = new RenameSwitchDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(SWITCH, rfSwitch);
        fragment.setArguments(args);
        return fragment;
    }

    public RenameSwitchDialogFragment() {

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        rfSwitch = getArguments().getParcelable(SWITCH);
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        final SwitchNameListener listener = ((SwitchNameListener) getParentFragment());

        final LayoutInflater inflater = getActivity().getLayoutInflater();

        final View view = inflater.inflate(R.layout.dialog_rename_switch, null);
        final EditText editText = (EditText) view.findViewById(R.id.switch_name);

        editText.setText(rfSwitch.getName());


        return new AlertDialog.Builder(getActivity()).setTitle(R.string.rename_switch)
                .setView(view)
                .setPositiveButton(R.string.rename, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        rfSwitch.setName(editText.getText().toString());
                        listener.onSwitchRenamed(rfSwitch);
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

    interface SwitchNameListener {
        void onSwitchRenamed(RfSwitch rfSwitch);
    }
}
