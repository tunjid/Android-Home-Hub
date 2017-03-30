package com.tunjid.rcswitchcontrol.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.services.ServerNsdService;

import static android.content.Context.BIND_AUTO_CREATE;
import static android.content.Context.MODE_PRIVATE;
import static com.tunjid.rcswitchcontrol.model.RcSwitch.SWITCH_PREFS;
import static com.tunjid.rcswitchcontrol.services.ServerNsdService.SERVICE_NAME_KEY;


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

        final ServiceConnection serviceConnection = (ServiceConnection) getParentFragment();
        final LayoutInflater inflater = getActivity().getLayoutInflater();

        final View view = inflater.inflate(R.layout.dialog_rename_switch, null);
        final EditText editText = (EditText) view.findViewById(R.id.switch_name);

        return new AlertDialog.Builder(getActivity()).setTitle(R.string.name_nsd_service)
                .setView(view)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        Activity activity = getActivity();

                        activity.getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE)
                                .edit().putString(SERVICE_NAME_KEY, editText.getText().toString())
                                .putBoolean(ServerNsdService.SERVER_FLAG, true).apply();

                        Intent serviceIntent = new Intent(activity, ServerNsdService.class);
                        activity.startService(serviceIntent);
                        activity.bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);

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
}
