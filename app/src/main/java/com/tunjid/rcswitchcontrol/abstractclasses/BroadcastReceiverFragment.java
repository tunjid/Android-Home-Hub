package com.tunjid.rcswitchcontrol.abstractclasses;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.util.List;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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
        for (String filter : filters()) intentFilter.addAction(filter);
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(receiver);
        super.onDestroy();
    }

    protected abstract void onReceive(Context context, Intent intent);

    protected abstract List<String> filters();
}
