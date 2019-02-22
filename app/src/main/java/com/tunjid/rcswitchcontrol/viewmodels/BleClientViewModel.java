package com.tunjid.rcswitchcontrol.viewmodels;

import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.tunjid.androidbootstrap.core.components.ServiceConnection;
import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster;
import com.tunjid.rcswitchcontrol.model.RcSwitch;
import com.tunjid.rcswitchcontrol.services.ClientBleService;
import com.tunjid.rcswitchcontrol.services.ClientNsdService;
import com.tunjid.rcswitchcontrol.services.ServerNsdService;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import io.reactivex.Flowable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.processors.PublishProcessor;

import static android.content.Context.MODE_PRIVATE;
import static com.tunjid.rcswitchcontrol.model.RcSwitch.SWITCH_PREFS;
import static com.tunjid.rcswitchcontrol.services.ClientBleService.ACTION_CONTROL;
import static com.tunjid.rcswitchcontrol.services.ClientBleService.ACTION_SNIFFER;
import static com.tunjid.rcswitchcontrol.services.ClientBleService.BLUETOOTH_DEVICE;
import static com.tunjid.rcswitchcontrol.services.ClientBleService.C_HANDLE_CONTROL;
import static com.tunjid.rcswitchcontrol.services.ClientBleService.C_HANDLE_TRANSMITTER;
import static com.tunjid.rcswitchcontrol.services.ClientBleService.STATE_SNIFFING;
import static com.tunjid.rcswitchcontrol.services.ServerNsdService.SERVICE_NAME_KEY;

public class BleClientViewModel extends AndroidViewModel {

    private BluetoothDevice device;

    private final List<RcSwitch> switches;
    private final RcSwitch.SwitchCreator switchCreator;
    private final CompositeDisposable disposable;
    private final PublishProcessor<Intent> intents;
    private final PublishProcessor<String> connectionState;
    private final PublishProcessor<Boolean> serverState;
    private final ServiceConnection<ClientBleService> bleConnection;
    private final ServiceConnection<ServerNsdService> serverConnection;

    public BleClientViewModel(@NonNull Application application) {
        super(application);


        switchCreator = new RcSwitch.SwitchCreator();
        disposable = new CompositeDisposable();

        intents = PublishProcessor.create();
        connectionState = PublishProcessor.create();
        serverState = PublishProcessor.create();

        switches = new ArrayList<>(RcSwitch.getSavedSwitches());
        bleConnection = new ServiceConnection<>(ClientBleService.class, this::onBleServiceConnected);
        serverConnection = new ServiceConnection<>(ServerNsdService.class, service -> serverState.onNext(true));

        bleConnection.with(application).bind();

        disposable.add(Broadcaster.listen(
                ClientBleService.ACTION_GATT_CONNECTED,
                ClientBleService.ACTION_GATT_CONNECTING,
                ClientBleService.ACTION_GATT_DISCONNECTED,
                ClientBleService.ACTION_GATT_SERVICES_DISCOVERED,
                ClientBleService.ACTION_CONTROL,
                ClientBleService.ACTION_SNIFFER,
                ClientBleService.DATA_AVAILABLE_UNKNOWN
        ).subscribe(this::onIntentReceived, Throwable::printStackTrace));
    }

    @Override protected void onCleared() {
        super.onCleared();

        disposable.clear();
        serverConnection.unbindService();
        if (bleConnection.isBound()) bleConnection.getBoundService().onAppBackground();
    }

    public List<RcSwitch> getSwitches() { return switches; }

    public Flowable<Intent> listenBle(BluetoothDevice device) {
        this.device = device;
        Bundle extras = new Bundle();
        extras.putParcelable(BLUETOOTH_DEVICE, device);

        bleConnection.with(getApplication()).setExtras(extras).bind();

        return intents;
    }

    public Flowable<Boolean> listenServer() {
        Context context = getApplication();
        if (context.getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE).getBoolean(ServerNsdService.SERVER_FLAG, false)) {
            serverConnection.with(context).start();
            serverConnection.with(context).bind();
        }

        return serverState.startWith(serverConnection.isBound());
    }

    public boolean isBleBound() { return bleConnection.isBound(); }

    public boolean isBleConnected() { return bleConnection.isBound() && !bleConnection.getBoundService().isConnected(); }

    public Flowable<String> connectionState() {
        return connectionState.startWith(publisher -> {
            boolean bound = bleConnection.isBound();
            if (bound) bleConnection.getBoundService().onAppForeGround();

            publisher.onNext(getConnectionText((bound
                    ? bleConnection.getBoundService().getConnectionState()
                    : ClientNsdService.ACTION_SOCKET_DISCONNECTED)));
            publisher.onComplete();
        });
    }

    public void reconnectBluetooth() {
        bleConnection.getBoundService().connect(device);
    }

    public void disconnectBluetooth() {
        bleConnection.getBoundService().disconnect();
    }

    public void sniffRcSwitch() {
        if (bleConnection.isBound()) bleConnection.getBoundService()
                .writeCharacteristicArray(C_HANDLE_CONTROL, new byte[]{STATE_SNIFFING});
    }

    public void toggleSwitch(RcSwitch rcSwitch, boolean state) {
        if (bleConnection.isBound()) bleConnection.getBoundService()
                .writeCharacteristicArray(C_HANDLE_TRANSMITTER, rcSwitch.getTransmission(state));
    }

    public int onSwitchUpdated(RcSwitch rcSwitch) {
        RcSwitch.saveSwitches(switches);
        return switches.indexOf(rcSwitch);
    }

    public String getSniffButtonText() {
        Context context = getApplication();
        return context.getString(R.string.sniff_code, context.getString(switchCreator.getState().equals(RcSwitch.ON_CODE)
                ? R.string.on
                : R.string.off));
    }

    public void forgetBluetoothDevice() {
        Broadcaster.push(new Intent(ServerNsdService.ACTION_STOP));
        ClientBleService clientBleService = bleConnection.getBoundService();

        clientBleService.disconnect();
        clientBleService.close();

        getApplication().getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE).edit()
                .remove(ClientBleService.LAST_PAIRED_DEVICE)
                .remove(ServerNsdService.SERVER_FLAG).apply();
    }

    public void restartServer() {
        if (serverConnection.isBound()) serverConnection.getBoundService().restart();
    }

    public void nameServer(String name) {
        Context context = getApplication();

        context.getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE)
                .edit().putString(SERVICE_NAME_KEY, name)
                .putBoolean(ServerNsdService.SERVER_FLAG, true).apply();

        serverConnection.with(context).start();
        serverConnection.with(context).bind();
    }

    private void onBleServiceConnected(ClientBleService service) {
        getConnectionText(service.getConnectionState());
    }

    private void onIntentReceived(Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        switch (action) {
            case ClientBleService.ACTION_GATT_CONNECTED:
            case ClientBleService.ACTION_GATT_CONNECTING:
            case ClientBleService.ACTION_GATT_DISCONNECTED:
                connectionState.onNext(getConnectionText(action));
                break;
            case ACTION_CONTROL:
                intents.onNext(intent);
                break;
            case ACTION_SNIFFER: {
                byte[] rawData = intent.getByteArrayExtra(ClientBleService.DATA_AVAILABLE_SNIFFER);
                switch (switchCreator.getState()) {
                    case RcSwitch.ON_CODE:
                        switchCreator.withOnCode(rawData);
                        break;
                    case RcSwitch.OFF_CODE:
                        RcSwitch rcSwitch = switchCreator.withOffCode(rawData);
                        rcSwitch.setName("Switch " + (switches.size() + 1));

                        if (switches.contains(rcSwitch)) return;

                        switches.add(rcSwitch);
                        RcSwitch.saveSwitches(switches);
                        break;
                }
                break;
            }
        }
    }

    private String getConnectionText(String newState) {
        Context context = getApplication();
        switch (newState) {
            case ClientBleService.ACTION_GATT_CONNECTED:
                return context.getString(R.string.connected);
            case ClientBleService.ACTION_GATT_CONNECTING:
                return context.getString(R.string.connecting);
            case ClientBleService.ACTION_GATT_DISCONNECTED:
                return context.getString(R.string.disconnected);
            default:
                return "";
        }
    }
}
