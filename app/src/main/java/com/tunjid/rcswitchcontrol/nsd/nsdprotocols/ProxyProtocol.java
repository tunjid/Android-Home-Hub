package com.tunjid.rcswitchcontrol.nsd.nsdprotocols;

import com.tunjid.rcswitchcontrol.model.Payload;

import java.io.IOException;

/**
 * A protocol that proxies requests to another {@link CommsProtocol} of a user's choosing
 * <p>
 * Created by tj.dahunsi on 2/11/17.
 */

public class ProxyProtocol implements CommsProtocol {

    private static final String CHOOSER = "choose";
    private static final String KNOCK_KNOCK = "Knock Knock Jokes";
    private static final String RC_REMOTE = "RC Remote";

    private boolean choosing;

    private CommsProtocol commsProtocol;

    public ProxyProtocol() {
    }

    @Override
    public Payload processInput(String input) {
        Payload.Builder builder = Payload.builder();

        if (input == null) input = RESET;

        // First connection
        switch (input) {
            case RESET:
            case CHOOSER:
                choosing = true;
                builder.setResponse("Please choose the server you want, Knock Knock jokes, or an RC Remote");
                builder.addCommand(KNOCK_KNOCK);
                builder.addCommand(RC_REMOTE);
                builder.addCommand(RESET);
                return builder.build();
        }


        if (choosing) {
            switch (input) {
                case KNOCK_KNOCK:
                    commsProtocol = new KnockKnockProtocol();
                    break;
                case RC_REMOTE:
                    commsProtocol = new BleRcProtocol();
                    break;
                default:
                    builder.setResponse("Invalid command. Please choose the server you want, Knock Knock jokes, or an RC Remote");
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

            return builder.build();
        }
        return commsProtocol.processInput(input);
    }

    @Override
    public void close() throws IOException {
        if (commsProtocol != null) commsProtocol.close();
    }
}
