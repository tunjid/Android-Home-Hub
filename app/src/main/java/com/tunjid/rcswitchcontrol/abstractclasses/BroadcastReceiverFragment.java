package com.tunjid.rcswitchcontrol.abstractclasses;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.List;

/**
 * A Fragment listening to broadcast from the {@link LocalBroadcastManager}
 * <p>
 * Created by tj.dahunsi on 2/18/17.
 */

public abstract class BroadcastReceiverFragment extends BaseFragment {

    private final IntentFilter intentFilter = new IntentFilter();
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (getView() != null) BroadcastReceiverFragment.this.onReceive(context, intent);
        }
    };

    @Override public void onAttach(Context context) {
        super.onAttach(context);
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        for (String filter : filters()) intentFilter.addAction(filter);
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(receiver);
        super.onDestroy();
    }

    protected abstract void onReceive(Context context, Intent intent);

    protected abstract List<String> filters();
}
