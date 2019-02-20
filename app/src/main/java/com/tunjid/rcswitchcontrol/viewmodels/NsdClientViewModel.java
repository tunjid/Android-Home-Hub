package com.tunjid.rcswitchcontrol.viewmodels;

import android.app.Application;
import android.content.Intent;
import android.util.Pair;

import com.tunjid.androidbootstrap.core.components.ServiceConnection;
import com.tunjid.androidbootstrap.functions.Supplier;
import com.tunjid.rcswitchcontrol.model.Payload;
import com.tunjid.rcswitchcontrol.model.RcSwitch;
import com.tunjid.rcswitchcontrol.nsd.protocols.CommsProtocol;
import com.tunjid.rcswitchcontrol.services.ClientNsdService;
import com.tunjid.rcswitchcontrol.utils.Utils;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import io.reactivex.processors.PublishProcessor;

public class NsdClientViewModel extends AndroidViewModel {

    private final List<String> history;
    private final List<String> commands;
    private final List<RcSwitch> switches;

    private final ServiceConnection<ClientNsdService> nsdConnection;
    private final PublishProcessor<Pair<String, Intent>> processor;

    public NsdClientViewModel(@NonNull Application application) {
        super(application);

        history = new ArrayList<>();
        commands = new ArrayList<>();
        switches = new ArrayList<>();

        processor = PublishProcessor.create();
        nsdConnection = new ServiceConnection<>(ClientNsdService.class, this::onServiceConnected);
    }

    @Override protected void onCleared() {
        nsdConnection.unbindService();
        super.onCleared();
    }

    public List<String> getHistory() { return history; }

    public List<String> getCommands() { return commands; }

    public List<RcSwitch> getSwitches() { return switches; }

    private void onServiceConnected(ClientNsdService service) {
        //onConnectionStateChanged(service.getConnectionState());
        sendMessage(commands::isEmpty, Payload.builder().setAction(CommsProtocol.PING).build());
    }

    public void sendMessage(Payload message) {
        sendMessage(() -> true, message);
    }

    public void sendMessage(Supplier<Boolean> predicate, Payload message) {
        if (nsdConnection.isBound() && predicate.get()) nsdConnection.getBoundService().sendMessage(message);
    }
}
