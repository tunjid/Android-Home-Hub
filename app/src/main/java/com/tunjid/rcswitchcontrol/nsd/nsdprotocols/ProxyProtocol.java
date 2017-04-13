package com.tunjid.rcswitchcontrol.nsd.nsdprotocols;

import android.util.Log;

import com.tunjid.rcswitchcontrol.Application;
import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.model.Payload;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * A protocol that proxies requests to another {@link CommsProtocol} of a user's choosing
 * <p>
 * Created by tj.dahunsi on 2/11/17.
 */

public class ProxyProtocol extends CommsProtocol {

    private static final String TAG = ProxyProtocol.class.getSimpleName();

    private static final String CHOOSER = "choose";
    private static final String KNOCK_KNOCK = "Knock Knock Jokes";
    private static final String RC_REMOTE = "RC Remote";
    private static final String ANDROID_THINGS = "Android Things";

    private boolean choosing;

    private CommsProtocol commsProtocol;

    public ProxyProtocol(PrintWriter printWriter) {
        super(printWriter);
    }

    @Override
    public Payload processInput(String input) {
        Payload.Builder builder = Payload.builder();
        builder.setKey(getClass().getName());

        if (input == null) input = RESET;

        // First connection
        switch (input) {
            case RESET:
            case CHOOSER:
                try {
                    if (commsProtocol != null) commsProtocol.close();
                }
                catch (IOException e) {
                    Log.e(TAG, "Failed to close current CommsProtocul in ProxyProtocol", e);
                }

                choosing = true;
                builder.setResponse(appContext.getString(R.string.proxyprotocol_ping_response));
                if (Application.isAndroidThings()) builder.addCommand(ANDROID_THINGS);
                builder.addCommand(KNOCK_KNOCK);
                builder.addCommand(RC_REMOTE);
                builder.addCommand(RESET);
                return builder.build();
        }


        if (choosing) {
            switch (input) {
                case ANDROID_THINGS:
                    commsProtocol = new RemoteBleRcProtocol(printWriter);
                    break;
                case KNOCK_KNOCK:
                    commsProtocol = new KnockKnockProtocol();
                    break;
                case RC_REMOTE:
                    commsProtocol = new BleRcProtocol();
                    break;
                default:
                    builder.setResponse("Invalid command. Please choose the server you want, Knock Knock jokes, or an RC Remote");
                    if (Application.isAndroidThings()) builder.addCommand(ANDROID_THINGS);
                    builder.addCommand(KNOCK_KNOCK);
                    builder.addCommand(RC_REMOTE);
                    builder.addCommand(RESET);
                    return builder.build();
            }

            choosing = false;

            String result = "Chose Protocol: " + commsProtocol.getClass().getSimpleName();
            result += "\n";
            result += "\n";

            Payload payload = commsProtocol.processInput(null);

            builder.setKey(payload.getKey());
            builder.setData(payload.getData());
            builder.setResponse(result + payload.getResponse());

            for (String command : payload.getCommands()) builder.addCommand(command);
            builder.addCommand(RESET);

            return builder.build();
        }

        return commsProtocol.processInput(input);
    }

    @Override
    public void close() throws IOException {
        if (commsProtocol != null) commsProtocol.close();
    }
}
