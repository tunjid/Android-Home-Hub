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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

    private final Set<String> noisy;
    private final List<String> history;
    private final List<String> commands;
    private final List<RcSwitch> switches;

    private final CompositeDisposable disposable;
    private final PublishProcessor<State> stateProcessor;
    private final PublishProcessor<String> connectionStateProcessor;
    private final ServiceConnection<ClientNsdService> nsdConnection;

    public NsdClientViewModel(@NonNull Application app) {
        super(app);

        noisy = getNoisy(app);
        history = new ArrayList<>();
        commands = new ArrayList<>();
        switches = new ArrayList<>();

        stateProcessor = PublishProcessor.create();
        connectionStateProcessor = PublishProcessor.create();

        disposable = new CompositeDisposable();
        nsdConnection = new ServiceConnection<>(ClientNsdService.class, this::onServiceConnected);

        nsdConnection.with(app).bind();

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
        nsdConnection.unbindService();
        super.onCleared();
    }

    public List<String> getHistory() { return history; }

    public List<String> getCommands() { return commands; }

    public List<RcSwitch> getSwitches() { return switches; }

    public Flowable<State> listen() {
        return stateProcessor.observeOn(mainThread());
    }

    public boolean isBound() { return nsdConnection.isBound(); }

    public boolean isConnected() { return nsdConnection.isBound() && !nsdConnection.getBoundService().isConnected(); }

    public int getLatestHistoryIndex() {
        return history.size() - 1;
    }

    public void sendMessage(Payload message) { sendMessage(() -> true, message); }

    public void onBackground() { nsdConnection.getBoundService().onAppBackground(); }

    public void forgetService() {
        // Don't call unbind, when the hosting activity is finished,
        // onDestroy will be called and the connection unbound
        if (nsdConnection.isBound()) nsdConnection.getBoundService().stopSelf();

        getApplication().getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE).edit()
                .remove(ClientNsdService.LAST_CONNECTED_SERVICE).apply();
    }

    public Flowable<String> connectionState() {
        return connectionStateProcessor.startWith(publisher -> {
            boolean bound = nsdConnection.isBound();
            if (bound) nsdConnection.getBoundService().onAppForeGround();

            publisher.onNext(getConnectionText(bound
                    ? nsdConnection.getBoundService().getConnectionState()
                    : ClientNsdService.ACTION_SOCKET_DISCONNECTED));
            publisher.onComplete();
        }).observeOn(mainThread());
    }

    private void onServiceConnected(ClientNsdService service) {
        connectionStateProcessor.onNext(getConnectionText(service.getConnectionState()));
        sendMessage(commands::isEmpty, Payload.builder().setAction(CommsProtocol.PING).build());
    }

    private void onIntentReceived(Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        switch (action) {
            case ClientNsdService.ACTION_SOCKET_CONNECTED:
            case ClientNsdService.ACTION_SOCKET_CONNECTING:
            case ClientNsdService.ACTION_SOCKET_DISCONNECTED:
                connectionStateProcessor.onNext(getConnectionText(action));
                break;
            case ClientNsdService.ACTION_START_NSD_DISCOVERY:
                connectionStateProcessor.onNext(getConnectionText(ClientNsdService.ACTION_SOCKET_CONNECTING));
                break;
            case ClientNsdService.ACTION_SERVER_RESPONSE:
                String serverResponse = intent.getStringExtra(ClientNsdService.DATA_SERVER_RESPONSE);
                Payload payload = Payload.deserialize(serverResponse);
                boolean isSwitchPayload = isSwitchPayload(payload);

                Lists.replace(commands, payload.getCommands());
                Single<DiffResult> diff = isSwitchPayload
                        ? diff(switches, () -> diffSwitches(payload))
                        : diff(history, () -> diffHistory(payload));

                disposable.add(diff.map(diffResult -> new State(isSwitchPayload, getMessage(payload), diffResult))
                        .subscribe(stateProcessor::onNext, Throwable::printStackTrace));
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

    @Nullable
    private String getMessage(Payload payload) {
        String response = payload.getResponse();
        if (response == null) return null;

        String action = payload.getAction();
        action = action == null ? "" : action;

        return noisy.contains(action) ? response : null;
    }

    private Set<String> getNoisy(@NonNull Application app) {
        return new HashSet<>(Arrays.asList(
                ClientBleService.ACTION_TRANSMITTER,
                app.getString(R.string.scanblercprotocol_sniff),
                app.getString(R.string.blercprotocol_rename_command),
                app.getString(R.string.blercprotocol_delete_command),
                app.getString(R.string.blercprotocol_refresh_switches_command)
        ));
    }

    private boolean hasSwitches(Payload payload) {
        String action = payload.getAction();
        if (action == null) return false;

        Context context = getApplication();
        return action.equals(ClientBleService.ACTION_TRANSMITTER)
                || action.equals(context.getString(R.string.blercprotocol_delete_command))
                || action.equals(context.getString(R.string.blercprotocol_rename_command));
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
                hasSwitches(payload)
                        ? RcSwitch.deserializeSavedSwitches(payload.getData())
                        : Collections.emptyList(),
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

    public static final class State {

        public final boolean isRc;
        @Nullable public final String prompt;
        @NonNull public final DiffResult result;

        State(boolean isRc, @Nullable String prompt, @NonNull DiffResult result) {
            this.isRc = isRc;
            this.prompt = prompt;
            this.result = result;
        }
    }
}
