package com.tunjid.rcswitchcontrol.dialogfragments;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.model.RcSwitch;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;


@SuppressLint("InflateParams")
public class RenameSwitchDialogFragment extends DialogFragment {

    private static final String SWITCH = "SWITCH";

    private RcSwitch rcSwitch;

    public static RenameSwitchDialogFragment newInstance(RcSwitch rcSwitch) {

        RenameSwitchDialogFragment fragment = new RenameSwitchDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(SWITCH, rcSwitch);
        fragment.setArguments(args);
        return fragment;
    }

    public RenameSwitchDialogFragment() {

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rcSwitch = Objects.requireNonNull(getArguments()).getParcelable(SWITCH);
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final SwitchNameListener listener = ((SwitchNameListener) getParentFragment());

        FragmentActivity activity = requireActivity();
        final LayoutInflater inflater = activity.getLayoutInflater();

        final View view = inflater.inflate(R.layout.dialog_rename_switch, null);
        final EditText editText = view.findViewById(R.id.switch_name);

        editText.setText(rcSwitch.getName());

        return new AlertDialog.Builder(activity).setTitle(R.string.rename_switch)
                .setView(view)
                .setPositiveButton(R.string.rename, (dialog, id) -> {
                    rcSwitch.setName(editText.getText().toString());
                    Objects.requireNonNull(listener).onSwitchRenamed(rcSwitch);
                    dismiss();
                })
                .setNegativeButton(R.string.cancel, (dialog, id) -> dismiss())
                .create();
    }

    public interface SwitchNameListener {
        void onSwitchRenamed(RcSwitch rcSwitch);
    }
}
