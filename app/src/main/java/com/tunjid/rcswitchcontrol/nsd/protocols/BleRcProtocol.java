package com.tunjid.rcswitchcontrol.nsd.protocols;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.tunjid.rcswitchcontrol.R;
import com.tunjid.androidbootstrap.core.components.ServiceConnection;
import com.tunjid.rcswitchcontrol.model.Payload;
import com.tunjid.rcswitchcontrol.model.RcSwitch;
import com.tunjid.rcswitchcontrol.services.ClientBleService;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

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

    private final List<RcSwitch> switches;
    private final Handler pushHandler;
    private final HandlerThread pushThread;
    private final RcSwitch.SwitchCreator switchCreator;
    private final ServiceConnection<ClientBleService> bleConnection;
    private final BleBroadcastReceiver bleReceiver;

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

        switches = RcSwitch.getSavedSwitches();
        switchCreator = new RcSwitch.SwitchCreator();

        pushHandler = new Handler(pushThread.getLooper());
        bleConnection = new ServiceConnection<>(ClientBleService.class);
        bleReceiver = new BleBroadcastReceiver();

        bleConnection.with(appContext).bind();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ClientBleService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(ClientBleService.ACTION_GATT_CONNECTING);
        intentFilter.addAction(ClientBleService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(ClientBleService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(ClientBleService.ACTION_CONTROL);
        intentFilter.addAction(ClientBleService.ACTION_SNIFFER);
        intentFilter.addAction(ClientBleService.DATA_AVAILABLE_UNKNOWN);

        LocalBroadcastManager.getInstance(appContext).registerReceiver(bleReceiver, intentFilter);
    }

    @Override
    public void close() throws IOException {
        LocalBroadcastManager.getInstance(appContext).unregisterReceiver(bleReceiver);

        pushThread.quitSafely();
        if (bleConnection.isBound()) bleConnection.unbindService();
    }

    @Override
    public Payload processInput(Payload input) {
        Resources resources = appContext.getResources();
        Payload.Builder builder = Payload.builder();
        builder.setKey(getClass().getName()).addCommand(RESET);

        String action = input.getAction();

        if (action.equals(PING)) {
            builder.setResponse(resources.getString(R.string.blercprotocol_ping_response))
                    .setAction(ClientBleService.ACTION_TRANSMITTER)
                    .setData(RcSwitch.serializedSavedSwitches())
                    .addCommand(REFRESH_SWITCHES).addCommand(SNIFF);
        }
        else if (action.equals(REFRESH_SWITCHES)) {
            builder.setResponse(resources.getString(R.string.blercprotocol_refresh_response))
                    .setAction(ClientBleService.ACTION_TRANSMITTER)
                    .setData(RcSwitch.serializedSavedSwitches())
                    .addCommand(SNIFF).addCommand(REFRESH_SWITCHES);
        }
        else if (action.equals(SNIFF)) {
            builder.addCommand(RESET).addCommand(DISCONNECT);

            if (bleConnection.isBound()) {
                builder.setResponse(appContext.getString(R.string.blercprotocol_start_sniff_response));
                bleConnection.getBoundService().writeCharacteristicArray(
                        ClientBleService.C_HANDLE_CONTROL,
                        new byte[]{ClientBleService.STATE_SNIFFING});
            }
            else {
                builder.setResponse(appContext.getString(R.string.blercprotocol_start_sniff_response));
            }
        }
        else if (action.equals(RENAME)) {
            RcSwitch rcSwitch = RcSwitch.deserialize(input.getData());
            int position = switches.indexOf(rcSwitch);

            // Switches are equal based on their codes, not thier names.
            // Remove the switch with the old name, and add the switch with the new name.
            if (position != -1) {
                RcSwitch oldSwitch = switches.get(position);
                builder.setResponse(resources.getString(R.string.blercprotocol_renamed_response, oldSwitch.getName(), rcSwitch.getName()));
                switches.remove(position);
                switches.add(rcSwitch);
                RcSwitch.saveSwitches(switches);
            }
            else {
                builder.setResponse(resources.getString(R.string.blercprotocol_no_such_switch_response));
            }

            builder.setAction(action).setData(RcSwitch.serializedSavedSwitches())
            .addCommand(SNIFF);
        }
        else if (action.equals(DELETE)) {
            RcSwitch rcSwitch = RcSwitch.deserialize(input.getData());
            String response = switches.remove(rcSwitch)
                    ? resources.getString(R.string.blercprotocol_deleted_response, rcSwitch.getName())
                    : resources.getString(R.string.blercprotocol_no_such_switch_response);

            // Save switches before sending them
            RcSwitch.saveSwitches(switches);

            builder.setResponse(response).setAction(action)
                    .setData(RcSwitch.serializedSavedSwitches()).addCommand(SNIFF);
        }
        else if (action.equals(ClientBleService.ACTION_TRANSMITTER)) {
            builder.setResponse(resources.getString(R.string.blercprotocol_transmission_response))
                    .addCommand(SNIFF).addCommand(REFRESH_SWITCHES);

            Intent intent = new Intent(ClientBleService.ACTION_TRANSMITTER);
            intent.putExtra(ClientBleService.DATA_AVAILABLE_TRANSMITTER, input.getData());

            LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent);
        }

        return builder.build();
    }

    /**
     * Processes the broadcasts from {@link ClientBleService}
     */
    private class BleBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            final Payload.Builder builder = Payload.builder();
            builder.setKey(BleRcProtocol.this.getClass().getName());

            switch (action) {
                case ClientBleService.ACTION_GATT_CONNECTED:
                    builder.addCommand(SNIFF);
                    builder.addCommand(DISCONNECT);
                    builder.setResponse(appContext.getString(R.string.connected));
                    break;
                case ClientBleService.ACTION_GATT_CONNECTING:
                    builder.setResponse(appContext.getString(R.string.connecting));
                    break;
                case ClientBleService.ACTION_GATT_DISCONNECTED:
                    builder.addCommand(CONNECT);
                    builder.setResponse(appContext.getString(R.string.disconnected));
                    break;
                case ClientBleService.ACTION_CONTROL: {
                    byte[] rawData = intent.getByteArrayExtra(ClientBleService.DATA_AVAILABLE_CONTROL);
                    if (rawData[0] == 1) {
                        builder.setResponse(appContext.getString(R.string.blercprotocol_stop_sniff_response))
                                .setAction(action).setData(String.valueOf(rawData[0]))
                                .addCommand(SNIFF).addCommand(RESET);
                    }
                    break;
                }
                case ClientBleService.ACTION_SNIFFER: {
                    byte[] rawData = intent.getByteArrayExtra(ClientBleService.DATA_AVAILABLE_SNIFFER);
                    builder.setData(switchCreator.getState());

                    switch (switchCreator.getState()) {
                        case RcSwitch.ON_CODE:
                            switchCreator.withOnCode(rawData);
                            builder.setResponse(appContext.getString(R.string.blercprotocol_sniff_on_response))
                                    .setAction(action).addCommand(SNIFF).addCommand(RESET);
                            break;
                        case RcSwitch.OFF_CODE:
                            RcSwitch rcSwitch = switchCreator.withOffCode(rawData);
                            rcSwitch.setName("Switch " + (switches.size() + 1));

                            builder.setAction(action).addCommand(SNIFF)
                                    .addCommand(REFRESH_SWITCHES).addCommand(RESET);

                            if (!switches.contains(rcSwitch)) {
                                switches.add(rcSwitch);
                                RcSwitch.saveSwitches(switches);
                                builder.setResponse(appContext.getString(R.string.blercprotocol_sniff_off_response))
                                        .setAction(ClientBleService.ACTION_TRANSMITTER).setData(RcSwitch.serializedSavedSwitches());
                            }
                            else {
                                builder.setResponse(appContext.getString(R.string.scanblercprotocol_sniff_already_exists_response));
                            }
                            break;
                    }
                    break;
                }
            }
            pushHandler.post(new Runnable() {
                @Override
                public void run() {
                    assert printWriter != null;
                    printWriter.println(builder.build().serialize());
                }
            });
            Log.i(TAG, "Received data for: " + action);
        }
    }
}
