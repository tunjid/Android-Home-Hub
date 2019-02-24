package com.tunjid.rcswitchcontrol.nsd.protocols;


import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.tunjid.androidbootstrap.core.components.ServiceConnection;
import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.broadcasts.Broadcaster;
import com.tunjid.rcswitchcontrol.model.Payload;
import com.tunjid.rcswitchcontrol.model.RcSwitch;
import com.tunjid.rcswitchcontrol.services.ClientBleService;

import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;

import io.reactivex.disposables.CompositeDisposable;

import static com.tunjid.rcswitchcontrol.services.ClientBleService.C_HANDLE_CONTROL;
import static com.tunjid.rcswitchcontrol.services.ClientBleService.STATE_SNIFFING;

/**
 * A protocol for communicating with RF 433 MhZ devices
 * <p>
 * Created by tj.dahunsi on 2/11/17.
 */

public class BleRcProtocol extends CommsProtocol {

    private static final String TAG = BleRcProtocol.class.getSimpleName();

    private final String SNIFF;
    private final String RENAME;
    private final String DELETE;
    private final String CONNECT;
    private final String DISCONNECT;
    private final String REFRESH_SWITCHES;

    private final Handler pushHandler;
    private final HandlerThread pushThread;
    private final RcSwitch.SwitchCreator switchCreator;
    private final ServiceConnection<ClientBleService> bleConnection;
    private final CompositeDisposable disposable;

    BleRcProtocol(PrintWriter printWriter) {
        super(printWriter);

        SNIFF = appContext.getString(R.string.scanblercprotocol_sniff);
        RENAME = appContext.getString(R.string.blercprotocol_rename_command);
        DELETE = appContext.getString(R.string.blercprotocol_delete_command);
        CONNECT = appContext.getString(R.string.connect);
        DISCONNECT = appContext.getString(R.string.menu_disconnect);
        REFRESH_SWITCHES = appContext.getString(R.string.blercprotocol_refresh_switches_command);

        pushThread = new HandlerThread("PushThread");
        pushThread.start();

        switchCreator = new RcSwitch.SwitchCreator();

        pushHandler = new Handler(pushThread.getLooper());
        bleConnection = new ServiceConnection<>(ClientBleService.class);
        disposable = new CompositeDisposable();

        bleConnection.with(appContext).bind();

        disposable.add(Broadcaster.listen(
                ClientBleService.ACTION_GATT_CONNECTED,
                ClientBleService.ACTION_GATT_CONNECTING,
                ClientBleService.ACTION_GATT_DISCONNECTED,
                ClientBleService.ACTION_GATT_SERVICES_DISCOVERED,
                ClientBleService.ACTION_CONTROL,
                ClientBleService.ACTION_SNIFFER,
                ClientBleService.DATA_AVAILABLE_UNKNOWN)
                .subscribe(this::onBleIntentReceived, Throwable::printStackTrace));
    }

    @Override
    public void close() {
        disposable.clear();

        pushThread.quitSafely();
        if (bleConnection.isBound()) bleConnection.unbindService();
    }

    @Override
    public Payload processInput(Payload input) {
        Payload.Builder builder = Payload.builder();
        builder.setKey(getClass().getName()).addCommand(RESET);

        String action = input.getAction();

        if (action.equals(PING)) {
            builder.setResponse(getString(R.string.blercprotocol_ping_response))
                    .setAction(ClientBleService.ACTION_TRANSMITTER)
                    .setData(RcSwitch.serializedSavedSwitches())
                    .addCommand(REFRESH_SWITCHES).addCommand(SNIFF);
        }
        else if (action.equals(REFRESH_SWITCHES)) {
            builder.setResponse(getString(R.string.blercprotocol_refresh_response))
                    .setAction(ClientBleService.ACTION_TRANSMITTER)
                    .setData(RcSwitch.serializedSavedSwitches())
                    .addCommand(SNIFF).addCommand(REFRESH_SWITCHES);
        }
        else if (action.equals(SNIFF)) {
            builder.addCommand(RESET)
                    .addCommand(DISCONNECT)
                    .setResponse(appContext.getString(R.string.blercprotocol_start_sniff_response));

            if (bleConnection.isBound()) bleConnection.getBoundService()
                    .writeCharacteristicArray(C_HANDLE_CONTROL, new byte[]{STATE_SNIFFING});
        }
        else if (action.equals(RENAME)) {
            List<RcSwitch> switches = RcSwitch.getSavedSwitches();
            RcSwitch rcSwitch = RcSwitch.deserialize(input.getData());

            int position = switches.indexOf(rcSwitch);
            boolean hasSwitch = position > -1;

            builder.setResponse(hasSwitch
                    ? getString(R.string.blercprotocol_renamed_response, switches.get(position).getName(), rcSwitch.getName())
                    : getString(R.string.blercprotocol_no_such_switch_response));

            // Switches are equal based on their codes, not their names.
            // Remove the switch with the old name, and add the switch with the new name.
            if (hasSwitch) {
                switches.remove(position);
                switches.add(position, rcSwitch);
                RcSwitch.saveSwitches(switches);
            }

            builder.setData(RcSwitch.serializedSavedSwitches())
                    .addCommand(SNIFF)
                    .setAction(action);
        }
        else if (action.equals(DELETE)) {
            List<RcSwitch> switches = RcSwitch.getSavedSwitches();
            RcSwitch rcSwitch = RcSwitch.deserialize(input.getData());
            String response = switches.remove(rcSwitch)
                    ? getString(R.string.blercprotocol_deleted_response, rcSwitch.getName())
                    : getString(R.string.blercprotocol_no_such_switch_response);

            // Save switches before sending them
            RcSwitch.saveSwitches(switches);

            builder.setResponse(response).setAction(action)
                    .setData(RcSwitch.serializedSavedSwitches())
                    .addCommand(SNIFF);
        }
        else if (action.equals(ClientBleService.ACTION_TRANSMITTER)) {
            builder.setResponse(getString(R.string.blercprotocol_transmission_response))
                    .addCommand(SNIFF)
                    .addCommand(REFRESH_SWITCHES);

            Broadcaster.push(new Intent(ClientBleService.ACTION_TRANSMITTER)
                    .putExtra(ClientBleService.DATA_AVAILABLE_TRANSMITTER, input.getData()));
        }

        return builder.build();
    }

    private void onBleIntentReceived(Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        final Payload.Builder builder = Payload.builder();
        builder.setKey(BleRcProtocol.this.getClass().getName());

        switch (action) {
            case ClientBleService.ACTION_GATT_CONNECTED:
                builder.addCommand(SNIFF)
                        .addCommand(DISCONNECT)
                        .setResponse(appContext.getString(R.string.connected));
                break;
            case ClientBleService.ACTION_GATT_CONNECTING:
                builder.setResponse(appContext.getString(R.string.connecting));
                break;
            case ClientBleService.ACTION_GATT_DISCONNECTED:
                builder.addCommand(CONNECT)
                        .setResponse(appContext.getString(R.string.disconnected));
                break;
            case ClientBleService.ACTION_CONTROL: {
                byte[] rawData = intent.getByteArrayExtra(ClientBleService.DATA_AVAILABLE_CONTROL);
                if (rawData[0] == 1)
                    builder.setResponse(getString(R.string.blercprotocol_stop_sniff_response))
                            .setAction(action)
                            .setData(String.valueOf(rawData[0]))
                            .addCommand(SNIFF)
                            .addCommand(RESET);
                break;
            }
            case ClientBleService.ACTION_SNIFFER: {
                byte[] rawData = intent.getByteArrayExtra(ClientBleService.DATA_AVAILABLE_SNIFFER);
                builder.setData(switchCreator.getState());

                if (RcSwitch.ON_CODE.equals(switchCreator.getState())) {
                    switchCreator.withOnCode(rawData);
                    builder.setResponse(appContext.getString(R.string.blercprotocol_sniff_on_response))
                            .setAction(action)
                            .addCommand(SNIFF)
                            .addCommand(RESET);
                }
                else if (RcSwitch.OFF_CODE.equals(switchCreator.getState())) {
                    List<RcSwitch> switches = RcSwitch.getSavedSwitches();
                    RcSwitch rcSwitch = switchCreator.withOffCode(rawData);
                    boolean containsSwitch = switches.contains(rcSwitch);

                    rcSwitch.setName("Switch " + (switches.size() + 1));

                    builder.setAction(action)
                            .addCommand(SNIFF)
                            .addCommand(REFRESH_SWITCHES)
                            .addCommand(RESET)
                            .setResponse(getString(containsSwitch
                                    ? R.string.scanblercprotocol_sniff_already_exists_response
                                    : R.string.blercprotocol_sniff_off_response));

                    if (!containsSwitch) {
                        switches.add(rcSwitch);
                        RcSwitch.saveSwitches(switches);
                        builder.setAction(ClientBleService.ACTION_TRANSMITTER)
                                .setData(RcSwitch.serializedSavedSwitches());
                    }
                }
                break;
            }
        }
        pushHandler.post(() -> Objects.requireNonNull(printWriter).println(builder.build().serialize()));
        Log.i(TAG, "Received data for: " + action);
    }
}
