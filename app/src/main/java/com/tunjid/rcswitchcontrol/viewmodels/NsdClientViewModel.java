package com.tunjid.rcswitchcontrol.viewmodels;

import android.app.Application;
import android.content.Context;
import android.content.Intent;

import com.tunjid.androidbootstrap.core.components.ServiceConnection;
import com.tunjid.androidbootstrap.functions.Supplier;
import com.tunjid.androidbootstrap.functions.collections.Lists;
import com.tunjid.androidbootstrap.recyclerview.diff.Diff;
import com.tunjid.androidbootstrap.recyclerview.diff.Differentiable;
import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster;
import com.tunjid.rcswitchcontrol.model.Payload;
import com.tunjid.rcswitchcontrol.model.RcSwitch;
import com.tunjid.rcswitchcontrol.nsd.protocols.BleRcProtocol;
import com.tunjid.rcswitchcontrol.nsd.protocols.CommsProtocol;
import com.tunjid.rcswitchcontrol.services.ClientBleService;
import com.tunjid.rcswitchcontrol.services.ClientNsdService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.lifecycle.AndroidViewModel;
import androidx.recyclerview.widget.DiffUtil.DiffResult;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.processors.PublishProcessor;

import static android.content.Context.MODE_PRIVATE;
import static com.tunjid.rcswitchcontrol.model.RcSwitch.SWITCH_PREFS;
import static io.reactivex.android.schedulers.AndroidSchedulers.mainThread;
import static io.reactivex.schedulers.Schedulers.io;

public class NsdClientViewModel extends AndroidViewModel {

    private final List<String> history;
    private final List<String> commands;
    private final List<RcSwitch> switches;

    private final CompositeDisposable disposable;
    private final PublishProcessor<String> connectionState;
    private final PublishProcessor<Pair<String, DiffResult>> payloads;
    private final ServiceConnection<ClientNsdService> nsdConnection;

    public NsdClientViewModel(@NonNull Application application) {
        super(application);

        history = new ArrayList<>();
        commands = new ArrayList<>();
        switches = new ArrayList<>();

        disposable = new CompositeDisposable();
        payloads = PublishProcessor.create();
        connectionState = PublishProcessor.create();
        nsdConnection = new ServiceConnection<>(ClientNsdService.class, this::onServiceConnected);
        nsdConnection.with(application).bind();

        disposable.add(Broadcaster.listen(
                ClientNsdService.ACTION_SOCKET_CONNECTED,
                ClientNsdService.ACTION_SOCKET_CONNECTING,
                ClientNsdService.ACTION_SOCKET_DISCONNECTED,
                ClientNsdService.ACTION_SERVER_RESPONSE,
                ClientNsdService.ACTION_START_NSD_DISCOVERY)
                .subscribe(this::onIntentReceived, Throwable::printStackTrace));
    }

    @Override
    protected void onCleared() {
        disposable.clear();
        nsdConnection.getBoundService().onAppBackground();
        nsdConnection.unbindService();
        super.onCleared();
    }

    public List<String> getHistory() { return history; }

    public List<String> getCommands() { return commands; }

    public List<RcSwitch> getSwitches() { return switches; }

    public Flowable<Pair<String, DiffResult>> listen() {
        return payloads.observeOn(mainThread());
    }

    public boolean isBound() { return nsdConnection.isBound(); }

    public boolean isConnected() { return nsdConnection.isBound() && !nsdConnection.getBoundService().isConnected(); }

    public int getLatestHistoryIndex() {
        return history.size() - 1;
    }

    public void sendMessage(Payload message) { sendMessage(() -> true, message); }

    public void forgetService() {
        // Don't call unbind, when the hosting activity is finished,
        // onDestroy will be called and the connection unbound
        if (nsdConnection.isBound()) nsdConnection.getBoundService().stopSelf();

        getApplication().getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE).edit()
                .remove(ClientNsdService.LAST_CONNECTED_SERVICE).apply();
    }

    public Flowable<String> connectionState() {
        return connectionState.startWith(publisher -> {
            boolean bound = nsdConnection.isBound();
            if (bound) nsdConnection.getBoundService().onAppForeGround();

            publisher.onNext(getConnectionText(bound
                    ? nsdConnection.getBoundService().getConnectionState()
                    : ClientNsdService.ACTION_SOCKET_DISCONNECTED));
            publisher.onComplete();
        }).observeOn(mainThread());
    }

    private void onServiceConnected(ClientNsdService service) {
        connectionState.onNext(getConnectionText(service.getConnectionState()));
        sendMessage(commands::isEmpty, Payload.builder().setAction(CommsProtocol.PING).build());
    }

    private void onIntentReceived(Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        switch (action) {
            case ClientNsdService.ACTION_SOCKET_CONNECTED:
            case ClientNsdService.ACTION_SOCKET_CONNECTING:
            case ClientNsdService.ACTION_SOCKET_DISCONNECTED:
                connectionState.onNext(getConnectionText(action));
                break;
            case ClientNsdService.ACTION_START_NSD_DISCOVERY:
                connectionState.onNext(getConnectionText(ClientNsdService.ACTION_SOCKET_CONNECTING));
                break;
            case ClientNsdService.ACTION_SERVER_RESPONSE:
                String serverResponse = intent.getStringExtra(ClientNsdService.DATA_SERVER_RESPONSE);
                Payload payload = Payload.deserialize(serverResponse);
                boolean isSwitchPayload = isSwitchPayload(payload);

                Lists.replace(commands, payload.getCommands());
                Single<DiffResult> diff = isSwitchPayload
                        ? diff(switches, () -> diffSwitches(payload))
                        : diff(history, () -> diffHistory(payload));

                diff.map(diffResult -> new Pair<>(isSwitchPayload ? payload.getResponse() : null, diffResult))
                        .subscribe(payloads::onNext, Throwable::printStackTrace);
                break;
        }
    }

    private String getConnectionText(String newState) {
        String text = "";
        Context context = getApplication();
        boolean isBound = nsdConnection.isBound();

        switch (newState) {
            case ClientNsdService.ACTION_SOCKET_CONNECTED:
                sendMessage(commands::isEmpty, Payload.builder().setAction(CommsProtocol.PING).build());
                text = !isBound
                        ? context.getString(R.string.connected)
                        : context.getString(R.string.connected_to, nsdConnection.getBoundService().getServiceName());
                break;
            case ClientNsdService.ACTION_SOCKET_CONNECTING:
                text = !isBound
                        ? context.getString(R.string.connecting)
                        : context.getString(R.string.connecting_to, nsdConnection.getBoundService().getServiceName());
                break;
            case ClientNsdService.ACTION_SOCKET_DISCONNECTED:
                text = context.getString(R.string.disconnected);
                break;
        }
        return text;
    }

    private void sendMessage(Supplier<Boolean> predicate, Payload message) {
        if (nsdConnection.isBound() && predicate.get())
            nsdConnection.getBoundService().sendMessage(message);
    }

    private boolean isSwitchPayload(Payload payload) {
        String key = payload.getKey();
        String payloadAction = payload.getAction();

        boolean isBleRc = key.equals(BleRcProtocol.class.getName());

        if (isBleRc) return true;
        if (payloadAction == null) return false;

        Context context = getApplication();
        return payloadAction.equals(ClientBleService.ACTION_TRANSMITTER)
                || payloadAction.equals(context.getString(R.string.blercprotocol_delete_command))
                || payloadAction.equals(context.getString(R.string.blercprotocol_rename_command));
    }

    private Diff<RcSwitch> diffSwitches(Payload payload) {
        return Diff.calculate(switches,
                RcSwitch.deserializeSavedSwitches(payload.getData()),
                (current, server) -> {
                    if (!server.isEmpty()) Lists.replace(current, server);
                    return current;
                },
                rcSwitch -> Differentiable.fromCharSequence(rcSwitch::serialize));
    }

    private Diff<String> diffHistory(Payload payload) {
        return Diff.calculate(history,
                Collections.singletonList(payload.getResponse()),
                (current, server) -> {
                    current.addAll(server);
                    return current;
                },
                response -> Differentiable.fromCharSequence(response::toString));
    }

    private <T> Single<DiffResult> diff(List<T> list, Supplier<Diff<T>> diffSupplier) {
        return Single.fromCallable(diffSupplier::get)
                .subscribeOn(io())
                .observeOn(mainThread())
                .doOnSuccess(diff -> Lists.replace(list, diff.cumulative))
                .map(diff -> diff.result);
    }
}
