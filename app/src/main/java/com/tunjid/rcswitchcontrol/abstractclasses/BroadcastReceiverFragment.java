package com.tunjid.rcswitchcontrol.abstractclasses;

import android.content.Intent;

import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster;

import java.util.List;

public abstract class BroadcastReceiverFragment extends BaseFragment {

    @Override public void onResume() {
        super.onResume();
        disposables.add(Broadcaster.listen(filters()).subscribe(intent -> {
            if (getView() != null) onReceive(intent);
        }, Throwable::printStackTrace));
    }

    protected abstract void onReceive(Intent intent);

    protected abstract List<String> filters();
}
